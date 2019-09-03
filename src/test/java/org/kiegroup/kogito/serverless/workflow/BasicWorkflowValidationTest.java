package org.kiegroup.kogito.serverless.workflow;

import java.io.IOException;
import java.io.InputStream;

import javax.json.Json;
import javax.json.JsonObject;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.serverless.workflow.api.Workflow;
import org.serverless.workflow.api.mapper.WorkflowObjectMapper;
import org.serverless.workflow.api.validation.WorkflowValidator;

public class BasicWorkflowValidationTest {

    private final WorkflowObjectMapper mapper = new WorkflowObjectMapper();

    @Test
    public void testWorkflow() throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("age-evaluation.json");
        Workflow workflow = mapper.readValue(is, Workflow.class);
        WorkflowValidator validator = new WorkflowValidator().forWorkflow(workflow);
        Assertions.assertNotNull(validator);
        Assertions.assertEquals(0, validator.validate().size());
    }

}
