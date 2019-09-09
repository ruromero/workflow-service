package org.kiegroup.kogito.serverless.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.kie.api.definition.process.Process;
import org.serverless.workflow.api.Workflow;
import org.serverless.workflow.api.interfaces.State;
import org.serverless.workflow.api.states.EndState;
import org.serverless.workflow.api.states.OperationState;
import org.serverless.workflow.api.states.SwitchState;

public class Graph {

    public static final String BACKUP_DATA_VAR = "backup-data";

    private static final String PROCESS_ID = "default-workflow";
    private static final String PROCESS_NAME = "Default workflow";
    private static final String PACKAGE_NAME = "org.kiegroup.kogito.workflow";

    private static final ObjectDataType JSON_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());
    private static final ObjectDataType JSON_BACKUP_DATA_TYPE = new ObjectDataType(JsonModel.class.getName());

    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final RuleFlowProcessFactory factory;
    private final String processId;
    private Long count = 0l;

    public Graph(Workflow workflow) {
        this.processId = Optional.ofNullable(workflow.getId()).orElse(PROCESS_ID);
        this.factory = RuleFlowProcessFactory.createProcess(processId)
            .name(Optional.ofNullable(workflow.getName()).orElse(PROCESS_NAME))
            .packageName(PACKAGE_NAME)
            .variable(JsonModel.DATA_PARAM, JSON_DATA_TYPE)
            .variable(BACKUP_DATA_VAR, JSON_BACKUP_DATA_TYPE);
        workflow.getStates().forEach(this::readState);
    }

    public Map<String, GraphNode> getNodes() {
        return nodes;
    }

    public Process getProcess() {
        nodes.values().forEach(node -> node.build(factory));
        nodes.values().forEach(node -> node.connectNextState(factory));
        return factory.validate().getProcess();
    }

    public String getProcessId() {
        return processId;
    }

    private void readState(State state) {
        switch (state.getType()) {
            case OPERATION:
                nodes.put(state.getName(), new OperationNode(this, (OperationState) state));
                break;
            case SWITCH:
                nodes.put(state.getName(), new SwitchNode(this, (SwitchState) state));
                break;
            case END:
                nodes.put(state.getName(), new EndNode(this, (EndState) state));
                break;
            default:
                throw new IllegalArgumentException("State not yet implemented: " + state.getType());
        }
    }

    Long getNodeId(String name) {
        if (nodes.containsKey(name)) {
            return nodes.get(name).getHeaderId();
        }
        return null;
    }

    Long getNextId() {
        return count++;
    }
}
