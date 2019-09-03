package org.kiegroup.kogito.serverless.process;

import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.ws.rs.core.MediaType;

import com.jayway.jsonpath.JsonPath;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.factory.WorkItemNodeFactory;
import org.kie.api.definition.process.Process;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.serverless.model.NodeRef;
import org.kiegroup.kogito.workitem.handler.LifecycleWorkItemHandler;
import org.kiegroup.kogito.workitem.handler.LogWorkItemHandler;
import org.kiegroup.kogito.workitem.handler.RestWorkItemHandler;
import org.serverless.workflow.api.Workflow;
import org.serverless.workflow.api.actions.Action;
import org.serverless.workflow.api.filters.Filter;
import org.serverless.workflow.api.interfaces.State;
import org.serverless.workflow.api.states.EndState;
import org.serverless.workflow.api.states.OperationState;

public class ProcessBuilder {

    private static final String START_NODE = "Start";
    private static final String PROCESS_ID = "default-workflow";
    private static final String PROCESS_NAME = "Default workflow";
    private static final String PACKAGE_NAME = "org.kiegroup.kogito.workflow";

    private static final ObjectDataType JSON_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());
    private static final ObjectDataType JSON_BACKUP_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());
    private static final String BACKUP_DATA_VAR = "backup-data";
    private static final String WORKITEM_TYPE = "Type";

    final Workflow workflow;
    final RuleFlowProcessFactory factory;
    final NodeRefBuilder refBuilder = new NodeRefBuilder();

    public ProcessBuilder(Workflow workflow) {
        this.workflow = workflow;
        this.factory = RuleFlowProcessFactory.createProcess(Optional.ofNullable(workflow.getId()).orElse(PROCESS_ID))
            .name(Optional.ofNullable(workflow.getName()).orElse(PROCESS_NAME))
            .packageName(PACKAGE_NAME)
            .variable(JsonModel.DATA_PARAM, JSON_DATA_TYPE)
            .variable(BACKUP_DATA_VAR, JSON_BACKUP_DATA_TYPE);
        buildStartNode();
        workflow.getStates().forEach(this::buildState);
        connectNodes();
        factory.validate();
    }

    public Process getProcess() {
        return factory.getProcess();
    }

    private void buildStartNode() {
        NodeRef ref = refBuilder.start(START_NODE);
        factory.startNode(ref.getId())
            .name(ref.getName())
            .done();
    }

    private void buildState(State state) {
        switch (state.getType()) {
            case OPERATION:
                buildOperationNode((OperationState) state);
                break;
            case END:
                buildEndNode((EndState) state);
                break;
            default:
                //TODO: Implement SWITCH, EVENT, DELAY
                throw new UnsupportedOperationException("state not supported: " + state.getType());
        }
    }

    private void connectNodes() {
        NodeRef ref = refBuilder.getStart();
        while (ref.hasTo()) {
            factory.connection(ref.getId(), ref.getTo().getId());
            ref = ref.getTo();
        }
    }

    private void buildOperationNode(OperationState state) {
        if (state.getFilter() != null) {
            buildInputMapping(state.getFilter());
        }
        if (OperationState.ActionMode.SEQUENTIAL.equals(state.getActionMode())) {
            for (Action action : state.getActions()) {
                buildAction(action);
            }
        } else {
            //TODO: Provide parallel action-mode
            throw new UnsupportedOperationException("Parallel action-mode not supported");
        }
        if (state.getFilter() != null) {
            buildOutputMapping(state.getFilter());
        }
    }

    private void buildEndNode(EndState state) {
        NodeRef ref = refBuilder.to(state.getName());
        factory
            .endNode(ref.getId())
            .name(ref.getName())
            .terminate(EndState.Status.SUCCESS.equals(state.getStatus()))
            .done();
    }

    private void buildInputMapping(Filter filter) {
        NodeRef ref = refBuilder.to("input-mapping", true);
        factory.actionNode(ref.getId())
            .name(ref.getName())
            .action(kcontext -> {
                JsonObject data = (JsonObject) kcontext.getVariable(JsonModel.DATA_PARAM);
                kcontext.setVariable(BACKUP_DATA_VAR, data);
                Object result = JsonPath.compile(filter.getInputPath()).read(data);
                kcontext.setVariable(JsonModel.DATA_PARAM, result);
            }).done();
    }

    private void buildOutputMapping(Filter filter) {
        NodeRef ref = refBuilder.to("output-mapping", true);
        factory.actionNode(ref.getId())
            .name(ref.getName())
            .action(kcontext -> {
                //TODO: This implementation is very weak
                JsonObject data = (JsonObject) kcontext.getVariable(JsonModel.DATA_PARAM);
                JsonPath resultPath = JsonPath.compile(filter.getResultPath());
                int lastIdx = filter.getOutputPath().lastIndexOf(".");
                JsonObject result = data;
                if (lastIdx != -1) {
                    JsonObject backup = (JsonObject) kcontext.getVariable(BACKUP_DATA_VAR);
                    JsonPath outputPath = JsonPath.compile(filter.getOutputPath().substring(0, lastIdx));
                    String key = filter.getOutputPath().substring(lastIdx + 1);
                    //TODO: ONLY String values are supported. Enhance
                    JsonString value = resultPath.read(data);
                    result = Json.createReader(new StringReader(JsonPath.parse(backup.toString()).put(outputPath, key, value.getString()).jsonString())).readObject();
                }
                kcontext.setVariable(JsonModel.DATA_PARAM, result);
            }).done();
    }

    private void buildAction(Action action) {
        String type = null;
        if (action.getFunction().getMetadata() != null) {
            type = action.getFunction().getMetadata().get(WORKITEM_TYPE);
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is mandatory in the function metadata for function: " + action.getFunction().getName());
        }
        NodeRef ref = refBuilder.to(action.getFunction().getName());
        WorkItemNodeFactory wi = factory.workItemNode(ref.getId())
            .name(ref.getName())
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
}
