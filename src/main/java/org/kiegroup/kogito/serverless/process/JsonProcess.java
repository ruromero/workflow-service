package org.kiegroup.kogito.serverless.process;

import org.kie.api.definition.process.Process;
import org.kie.kogito.Model;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kiegroup.kogito.serverless.Application;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.serverless.service.WorkflowService;

public class JsonProcess extends AbstractProcess<JsonModel> {

    private ProcessBuilder processBuilder;

    public JsonProcess(Application application, WorkflowService workflowService) {
        super(application.config().process());
        this.processBuilder = new ProcessBuilder(workflowService.get());
    }

    @Override
    public Process legacyProcess() {
        return processBuilder.getProcess();
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
}
