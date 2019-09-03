package org.kiegroup.kogito.serverless.process;

import org.kiegroup.kogito.serverless.model.NodeRef;

public class NodeRefBuilder {

    private int count = 0;
    private NodeRef start;
    private NodeRef current;

    NodeRef to(String name) {
        return to(name, false);
    }

    NodeRef to(String name, boolean appendId) {
        if (appendId) {
            name = name + count;
        }
        current.setTo(count++, name);
        current = current.getTo();
        return current;
    }

    NodeRef getCurrent() {
        return this.current;
    }

    NodeRef getStart() {
        return this.start;
    }

    public NodeRef start(String name) {
        this.start = new NodeRef(count++, name);
        this.current = this.start;
        return this.start;
    }

}
