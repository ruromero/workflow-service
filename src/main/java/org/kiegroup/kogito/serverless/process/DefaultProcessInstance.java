package org.kiegroup.kogito.serverless.process;

import java.util.Map;

import org.kie.api.runtime.process.ProcessRuntime;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kiegroup.kogito.serverless.model.JsonModel;

public class DefaultProcessInstance extends AbstractProcessInstance<JsonModel> {

    public DefaultProcessInstance(AbstractProcess<JsonModel> process, JsonModel variables, ProcessRuntime processRuntime) {
        super(process, variables, processRuntime);
    }

    @Override
    protected Map<String, Object> bind(JsonModel variables) {
        return variables.toMap();
    }

    @Override
    protected void unbind(JsonModel variables, Map<String, Object> vmap) {
        variables.fromMap(this.id(), vmap);
    }
}
