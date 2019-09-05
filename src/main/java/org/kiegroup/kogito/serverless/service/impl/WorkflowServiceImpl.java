package org.kiegroup.kogito.serverless.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kie.api.definition.process.Process;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.serverless.process.ProcessBuilder;
import org.kiegroup.kogito.serverless.service.WorkflowService;
import org.serverless.workflow.api.Workflow;
import org.serverless.workflow.api.mapper.WorkflowObjectMapper;
import org.serverless.workflow.api.validation.ValidationError;
import org.serverless.workflow.api.validation.WorkflowValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowServiceImpl.class);

    private static final String ENV_PROCESS_SOURCE = "process-source";
    private static final String ENV_FILE_WORKFLOW_PATH = "workflow-path";
    private static final String SOURCE_FILE = "file";
    private static final String SOURCE_K8S = "k8s";

    private Workflow workflow;
    private Process process;
    private String processId;
    private final WorkflowObjectMapper mapper = new WorkflowObjectMapper();

    @ConfigProperty(
        name = ENV_PROCESS_SOURCE,
        defaultValue = SOURCE_FILE
    )
    String processSource;

    @ConfigProperty(name = ENV_FILE_WORKFLOW_PATH)
    Optional<String> filePath;

    void onInit(@Observes StartupEvent event) {
        switch (processSource) {
            case SOURCE_FILE:
                setFileBasedWorkflow();
                break;
            case SOURCE_K8S:
                setK8sBasedWorkflow();
                break;
            default:
                throw new IllegalArgumentException("Unsupported process source configured: " + processSource);
        }
        if (workflow == null) {
            throw new IllegalStateException("Unable to load a valid process definition");
        }
    }

    @Override
    public Workflow get() {
        return workflow;
    }

    @Override
    public Process getProcess() {
        return process;
    }

    @Override
    public String getProcessId() {
        return processId;
    }

    private void setFileBasedWorkflow() {
        if (!filePath.isPresent()) {
            throw new IllegalArgumentException("Missing required environment variable for File based workflow definition: " + ENV_FILE_WORKFLOW_PATH);
        }
        Workflow workflow = null;
        try {
            byte[] file = Files.readAllBytes(Paths.get(filePath.get()));
            workflow = mapper.readValue(new ByteArrayInputStream(file), Workflow.class);
            updateWorkflow(workflow);
        } catch (IOException e) {
            logger.error("Unable to read provided workflow", e);
        }
        if (workflow != null) {
            List<ValidationError> validationErrors = new WorkflowValidator().forWorkflow(workflow).validate();
            if (!validationErrors.isEmpty()) {
                updateWorkflow(workflow);
            } else {
                logger.warn("Workflow not updated. Provided workflow has validation errors: {}", validationErrors);
            }
        }
    }

    private void updateWorkflow(Workflow workflow) {
        this.workflow = workflow;
        ProcessBuilder builder = new ProcessBuilder(workflow);
        this.process = builder.getProcess();
        this.processId = builder.getProcessId();
    }

    private void setK8sBasedWorkflow() {
        //TODO: Implement K8S Source
        throw new UnsupportedOperationException("Not implemented");
    }
}
