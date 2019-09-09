package org.kiegroup.kogito.serverless.model;

import java.util.List;

import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.factory.RuleSetNodeFactory;
import org.kiegroup.kogito.workitem.handler.LifecycleWorkItemHandler;
import org.serverless.workflow.api.interfaces.Choice;
import org.serverless.workflow.api.interfaces.State;
import org.serverless.workflow.api.states.SwitchState;

class SwitchNode extends GraphNode {

    private final SwitchState state;

    SwitchNode(Graph graph, SwitchState state) {
        super(graph);
        this.state = state;
    }

    @Override
    State getState() {
        return state;
    }

    @Override
    void buildNode(RuleFlowProcessFactory factory) {
        //TODO: Implement
        List<Choice> choices = this.state.getChoices();
        Long prevId = this.getId();
        Long id = this.getNextId();
        RuleSetNodeFactory rule = factory.ruleSetNode(id).name(this.state.getName())
            .inMapping(LifecycleWorkItemHandler.PARAM_CONTENT_DATA, JsonModel.DATA_PARAM)
            .outMapping(LifecycleWorkItemHandler.PARAM_RESULT, JsonModel.DATA_PARAM)
            .dmnGroup();
        rule.
        rule.done();
    }

    @Override
    void buildInput(RuleFlowProcessFactory factory) {
        buildActionNode(factory, kcontext -> buildInputAction(kcontext, state.getFilter()));
    }

    @Override
    void buildOutput(RuleFlowProcessFactory factory) {
        buildActionNode(factory, kcontext -> buildOutputAction(kcontext, state.getFilter()));
    }

    @Override
    String getNextState() {
        return state.getDefault();
    }
}
