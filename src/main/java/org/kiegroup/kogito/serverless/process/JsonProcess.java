package org.kiegroup.kogito.serverless.process;

import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.stream.JsonParser;
import javax.ws.rs.core.MediaType;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.factory.WorkItemNodeFactory;
import org.kie.api.definition.process.Process;
import org.kie.kogito.Model;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kiegroup.kogito.serverless.Application;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.serverless.service.WorkflowService;
import org.kiegroup.kogito.workitem.handler.LifecycleWorkItemHandler;
import org.kiegroup.kogito.workitem.handler.LogWorkItemHandler;
import org.kiegroup.kogito.workitem.handler.RestWorkItemHandler;
import org.serverless.workflow.api.Workflow;
import org.serverless.workflow.api.actions.Action;
import org.serverless.workflow.api.filters.Filter;
import org.serverless.workflow.api.interfaces.State;
import org.serverless.workflow.api.states.EndState;
import org.serverless.workflow.api.states.OperationState;

public class JsonProcess extends AbstractProcess<JsonModel> {

    public static final String PROCESS_ID = "default-workflow";
    public static final String PROCESS_NAME = "Default workflow";

    private static final String PACKAGE_NAME = "org.kiegroup.kogito.workflow";
    private static final ObjectDataType JSON_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());
    private static final ObjectDataType JSON_BACKUP_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());
    private static final String BACKUP_DATA_VAR = "backup-data";
    private static final String WORKITEM_TYPE = "Type";
    private static final String START_NODE = "Start";

    final Workflow workflow;
    final RuleFlowProcessFactory factory;
    private Process process;
    private int nodeCount;

    public JsonProcess(Application application, WorkflowService workflowService) {
        super(application.config().process());
        this.workflow = workflowService.get();
        this.factory = RuleFlowProcessFactory.createProcess(Optional.ofNullable(workflow.getId()).orElse(PROCESS_ID));
        //TODO: Refactor
        nodeCount = 0;
        factory.name(getName())
            .packageName(PACKAGE_NAME)
            .variable(JsonModel.DATA_PARAM, JSON_DATA_TYPE)
            .variable(BACKUP_DATA_VAR, JSON_BACKUP_DATA_TYPE)
            .startNode(nodeCount).name(START_NODE).done();
        NodeRef startNode = new NodeRef(nodeCount++, START_NODE);
        NodeRef nodeRef = startNode;
        for (State state : workflow.getStates()) {
            nodeRef = buildState(state, nodeRef);
        }
        connectNodes(startNode);
        this.process = factory.validate().getProcess();
    }

    @Override
    public Process legacyProcess() {
        return process;
    }

    @Override
    public ProcessInstance<JsonModel> createInstance(JsonModel value) {
        return new JsonProcessInstance(this, value, this.createLegacyProcessRuntime());
    }

    @Override
    public ProcessInstance<JsonModel> createInstance(Model value) {
        return this.createInstance((JsonModel) value);
    }

    @Override
    public org.kie.kogito.process.Process<JsonModel> configure() {
        super.configure();
        return this;
    }

    private String getName() {
        return Optional.ofNullable(workflow.getName()).orElse(PROCESS_NAME);
    }

    private void connectNodes(NodeRef from) {
        if (from != null && from.to != null) {
            factory.connection(from.id, from.to.id);
            connectNodes(from.to);
        }
    }

    private NodeRef buildState(State state, NodeRef nodeRef) {
        switch (state.getType()) {
            case OPERATION:
                return buildOperationNode((OperationState) state, nodeRef);
            case END:
                return buildEndNode((EndState) state, nodeRef);
            default:
                //TODO: Implement SWITCH, EVENT, DELAY
                throw new UnsupportedOperationException("state not supported: " + state.getType());
        }
    }

    private NodeRef buildOperationNode(OperationState state, NodeRef nodeRef) {
        NodeRef newRef = nodeRef;
        if (state.getFilter() != null) {
            newRef = buildInputMapping(state.getFilter(), newRef);
        }
        if (OperationState.ActionMode.SEQUENTIAL.equals(state.getActionMode())) {
            for (Action action : state.getActions()) {
                newRef = buildAction(action, newRef);
            }
        } else {
            //TODO: Provide parallel action-mode
            throw new UnsupportedOperationException("Parallel action-mode not supported");
        }
        if (state.getFilter() != null) {
            newRef = buildOutputMapping(state.getFilter(), newRef);
        }
        return newRef;
    }

    private NodeRef buildInputMapping(Filter filter, NodeRef nodeRef) {
        String name = "input-mapping-" + nodeCount;
        factory.actionNode(nodeCount)
            .name(name)
            .action(kcontext -> {
                JsonObject data = (JsonObject) kcontext.getVariable(JsonModel.DATA_PARAM);
                kcontext.setVariable(BACKUP_DATA_VAR, data);
                Object result = JsonPath.compile(filter.getInputPath()).read(data);
                kcontext.setVariable(JsonModel.DATA_PARAM, result);
            }).done();
        nodeRef.to = new NodeRef(nodeCount++, name);
        return nodeRef.to;
    }

    private NodeRef buildOutputMapping(Filter filter, NodeRef nodeRef) {
        String name = "output-mapping-" + nodeCount;
        factory.actionNode(nodeCount)
            .name(name)
            .action(kcontext -> {
                //TODO: This implementation is very weak
                JsonObject data = (JsonObject) kcontext.getVariable(JsonModel.DATA_PARAM);
                JsonPath resultPath = JsonPath.compile(filter.getResultPath());
                int lastIdx = filter.getOutputPath().lastIndexOf(".");
                JsonObject result = data;
                if(lastIdx != -1) {
                    JsonObject backup = (JsonObject) kcontext.getVariable(BACKUP_DATA_VAR);
                    JsonPath outputPath = JsonPath.compile(filter.getOutputPath().substring(0, lastIdx));
                    String key = filter.getOutputPath().substring(lastIdx + 1);
                    //TODO: ONLY String values are supported. Enhance
                    JsonString value = resultPath.read(data);
                    result = Json.createReader(new StringReader(JsonPath.parse(backup.toString()).put(outputPath, key, value.getString()).jsonString())).readObject();
                }
                kcontext.setVariable(JsonModel.DATA_PARAM, result);
            }).done();
        nodeRef.to = new NodeRef(nodeCount++, name);
        return nodeRef.to;
    }

    private NodeRef buildAction(Action action, NodeRef nodeRef) {
        String type = null;
        if (action.getFunction().getMetadata() != null) {
            type = action.getFunction().getMetadata().get(WORKITEM_TYPE);
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is mandatory in the function metadata for function: " + action.getFunction().getName());
        }
        nodeRef.to = new NodeRef(nodeCount++, action.getFunction().getName());
        WorkItemNodeFactory wi = factory.workItemNode(nodeRef.to.id)
            .name(nodeRef.to.name)
            .inMapping(LifecycleWorkItemHandler.PARAM_CONTENT_DATA, JsonModel.DATA_PARAM)
            .outMapping(LifecycleWorkItemHandler.PARAM_RESULT, JsonModel.DATA_PARAM)
            .workName(action.getFunction().getMetadata().get(LifecycleWorkItemHandler.PARAM_TYPE));
        if (RestWorkItemHandler.HANDLER_NAME.equals(type)) {
            buildRestWorkItem(wi, action);
        } else if (LogWorkItemHandler.HANDLER_NAME.equals(type)) {
            buildLogWorkItem(wi, action);
        } else {
            throw new IllegalArgumentException("Unsupported function type: " + type);
        }
        wi.done();
        return nodeRef.to;
    }

    private void buildRestWorkItem(WorkItemNodeFactory wi, Action action) {
        wi.workParameter(RestWorkItemHandler.PARAM_TASK_NAME, RestWorkItemHandler.HANDLER_NAME)
            .workParameter(RestWorkItemHandler.PARAM_CONTENT_TYPE, MediaType.APPLICATION_JSON);
        addWorkParameterFromMetadata(wi, RestWorkItemHandler.PARAM_METHOD, action.getFunction().getMetadata());
        addWorkParameterFromMetadata(wi, RestWorkItemHandler.PARAM_URL, action.getFunction().getMetadata());
        addWorkParameterFromMetadata(wi, RestWorkItemHandler.PARAM_CONTENT_TYPE, action.getFunction().getMetadata());
        //TODO: Implement retry
        //TODO: Implement timeout
    }

    private void buildLogWorkItem(WorkItemNodeFactory wi, Action action) {
        addWorkParameterFromMetadata(wi, LogWorkItemHandler.PARAM_LEVEL, action.getFunction().getMetadata());
        addWorkParameterFromMetadata(wi, LogWorkItemHandler.PARAM_MESSAGE, action.getFunction().getMetadata());
        addWorkParameterFromMetadata(wi, LogWorkItemHandler.PARAM_FIELD, action.getFunction().getMetadata());
    }

    private void addWorkParameterFromMetadata(WorkItemNodeFactory wi, String param, Map<String, String> metadata) {
        if (metadata == null || !metadata.containsKey(param)) {
            return;
        }
        wi.workParameter(param, metadata.get(param));
    }

    private NodeRef buildEndNode(EndState state, NodeRef nodeRef) {
        factory
            .endNode(nodeCount)
            .name(state.getName())
            .terminate(EndState.Status.SUCCESS.equals(state.getStatus()))
            .done();
        nodeRef.to = new NodeRef(nodeCount++, state.getName());
        return nodeRef.to;
    }

    private static class NodeRef {

        final int id;
        final String name;
        NodeRef to;

        private NodeRef(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
