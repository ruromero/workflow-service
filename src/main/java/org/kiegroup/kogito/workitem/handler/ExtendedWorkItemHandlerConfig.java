package org.kiegroup.kogito.workitem.handler;

import org.kie.kogito.process.impl.DefaultWorkItemHandlerConfig;

public class ExtendedWorkItemHandlerConfig extends DefaultWorkItemHandlerConfig {

    public ExtendedWorkItemHandlerConfig() {
        this.register(RestWorkItemHandler.HANDLER_NAME, new RestWorkItemHandler());
    }
}
