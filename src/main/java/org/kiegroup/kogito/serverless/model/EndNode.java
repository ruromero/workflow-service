package org.kiegroup.kogito.serverless.model;

import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.factory.EndNodeFactory;
import org.serverless.workflow.api.interfaces.State;
import org.serverless.workflow.api.states.EndState;

class EndNode extends GraphNode {

    private final EndState state;

    EndNode(Graph graph, EndState state) {
        super(graph);
        this.state = state;
    }

    @Override
    State getState() {
        return state;
    }

    @Override
    void buildNode(RuleFlowProcessFactory factory) {
        Long prevId = this.getId();
        Long id = this.getNextId();
        EndNodeFactory end = factory.endNode(id)
            .name(state.getName());
        if (state.getFilter() != null && state.getFilter().getInputPath() != null) {
            buildInputFilter(end);
        }
        end.terminate(EndState.Status.SUCCESS.equals(state.getStatus()))
            .done();
        connect(factory, prevId, id);
    }

    private void buildInputFilter(EndNodeFactory nodeFactory) {
        nodeFactory.action(kcontext -> buildInputAction(kcontext, state.getFilter()));
    }

    @Override
    String getNextState() {
        return null;
    }
}
