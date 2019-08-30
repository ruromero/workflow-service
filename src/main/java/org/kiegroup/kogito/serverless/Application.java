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
import org.kiegroup.kogito.serverless.process.DefaultProcess;

@Singleton
public class Application implements org.kie.kogito.Application {

    @Inject
    Config config;

    private final Collection<String> processIds = Arrays.asList(DefaultProcess.PROCESS_NAME);

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
            if (DefaultProcess.PROCESS_NAME.equals(processId)) {
                return new DefaultProcess(Application.this).configure();
            }
            return null;
        }

        @Override
        public Collection<String> processIds() {
            return processIds;
        }
    }
}
