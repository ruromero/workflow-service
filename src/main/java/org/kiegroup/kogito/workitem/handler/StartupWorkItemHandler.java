package org.kiegroup.kogito.workitem.handler;

import org.kie.api.runtime.process.WorkItemHandler;

public interface StartupWorkItemHandler extends WorkItemHandler {

    String PARAM_TYPE = "Type";
    String PARAM_CONTENT_DATA = "ContentData";
    String PARAM_RESULT = "Result";

    void onStartup();

    void onShutdown();
}
