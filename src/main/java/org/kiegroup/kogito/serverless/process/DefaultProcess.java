package org.kiegroup.kogito.serverless.process;

import javax.enterprise.event.Observes;
import javax.json.JsonObject;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.process.ProcessContext;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kiegroup.kogito.serverless.Application;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.workitem.handler.RestWorkItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultProcess extends AbstractProcess<JsonModel> {

    public static final String PROCESS_NAME = "default-process";

    private static final Logger logger = LoggerFactory.getLogger(DefaultProcess.class);

    private static final String PROCESS_SOURCE_ENV = "process-source";
    private static final String SOURCE_FILE = "file";
    private static final String SOURCE_K8S = "k8s";

    final Application application;

    @ConfigProperty(
        name = PROCESS_SOURCE_ENV,
        defaultValue = SOURCE_FILE
    )
    String processSource;

    Process process;

    void onInit(@Observes StartupEvent event) {
        switch (processSource) {
            case SOURCE_FILE:
                process = getFileBasedProcess();
                break;
            case SOURCE_K8S:
                process = getK8sBasedProcess();
                break;
            default:
                throw new IllegalArgumentException("Unsupported process source configured: " + processSource);
        }
        if (process == null) {
            throw new IllegalStateException("Unable to load a valid process definition");
        }
    }

    public DefaultProcess(Application application) {
        super(application.config().process());
        this.application = application;
    }

    @Override
    public Process legacyProcess() {
        return buildProcess();
    }

    @Override
    public ProcessInstance<JsonModel> createInstance(JsonModel value) {
        return new DefaultProcessInstance(this, value, this.createLegacyProcessRuntime());
    }

    private Process getFileBasedProcess() {
        return buildProcess();
    }

    private Process getK8sBasedProcess() {
        throw new UnsupportedOperationException("Implement!");
    }

    @Deprecated
    private Process buildProcess() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess(PROCESS_NAME);
        factory.name("Test workflow")
            .packageName("org.acme.kogito.serverless")
            .variable(JsonModel.DATA_PARAM, new ObjectDataType(JsonObject.class.getName()))
            // nodes
            .startNode(1).name("Start").done()
            .actionNode(2).name("Action")
            .action(
                new Action() {
                    @Override
                    public void execute(ProcessContext context) {
                        JsonObject data = (JsonObject) context.getVariable(JsonModel.DATA_PARAM);
                        logger.info("data: {}", data);
                        context.setVariable(JsonModel.DATA_PARAM, data);
                    }
                })
            .done()
            .actionNode(3).name("Action2")
            .action(
                new Action() {
                    @Override
                    public void execute(ProcessContext context) {
                        JsonObject data = (JsonObject) context.getVariable(JsonModel.DATA_PARAM);
                        logger.info("data: {}", data);
                    }
                })
            .done()
            .workItemNode(4)
            .inMapping(RestWorkItemHandler.PARAM_CONTENT_DATA, JsonModel.DATA_PARAM)
            .outMapping(RestWorkItemHandler.PARAM_RESULT, JsonModel.DATA_PARAM)
            .workName("Rest")
            .workParameter("TaskName", RestWorkItemHandler.HANDLER_NAME)
            .workParameter(RestWorkItemHandler.PARAM_METHOD, HttpMethod.POST)
            .workParameter(RestWorkItemHandler.PARAM_URL, "http://localhost:8080/age")
            .workParameter(RestWorkItemHandler.PARAM_CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .done()
            .endNode(5).name("End").done()
            // connections
            .connection(1, 2)
            .connection(2, 4)
            .connection(4, 3)
            .connection(3, 5);

        return factory.validate().getProcess();
    }
}
