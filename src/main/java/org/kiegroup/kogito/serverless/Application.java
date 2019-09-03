package org.kiegroup.kogito.serverless;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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
            return new JsonProcess(Application.this, workflowService).configure();
        }

        @Override
        public Collection<String> processIds() {
            return Collections.EMPTY_LIST;
        }
    }

    @Override
    public Processes processes() {
        return processes;
    }
}
