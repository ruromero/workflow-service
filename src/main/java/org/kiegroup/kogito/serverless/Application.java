package org.kiegroup.kogito.serverless;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.kie.kogito.Config;
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kiegroup.kogito.serverless.process.JsonProcess;
import org.kiegroup.kogito.serverless.service.WorkflowService;

@Singleton
public class Application implements org.kie.kogito.Application {

    @Inject
    Config config;

    @Inject
    WorkflowService workflowService;

    private final Collection<String> processIds = Arrays.asList(JsonProcess.PROCESS_ID);

    final Processes processes = new ProcessesImpl();

    @Override
    public Config config() {
        return config;
    }

    @Override
    public UnitOfWorkManager unitOfWorkManager() {
        return config.process().unitOfWorkManager();
    }

    public class ProcessesImpl implements Processes {

        @Override
        public Process<? extends Model> processById(String processId) {
            if (JsonProcess.PROCESS_ID.equals(processId)) {
                return new JsonProcess(Application.this, workflowService).configure();
            }
            return null;
        }

        @Override
        public Collection<String> processIds() {
            return processIds;
        }
    }

    @Override
    public Processes processes() {
        return processes;
    }

}
