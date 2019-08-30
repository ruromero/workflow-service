package org.kiegroup.kogito.serverless;

import javax.inject.Singleton;

import org.kie.kogito.Config;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.ProcessEventListenerConfig;
import org.kie.kogito.process.WorkItemHandlerConfig;
import org.kie.kogito.process.impl.DefaultProcessEventListenerConfig;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.rules.RuleConfig;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kiegroup.kogito.workitem.handler.ExtendedWorkItemHandlerConfig;

@Singleton
public class ApplicationConfig implements Config {

    private final WorkItemHandlerConfig workItemHandlerConfig = new ExtendedWorkItemHandlerConfig();
    private final ProcessEventListenerConfig processEventListenerConfig = new DefaultProcessEventListenerConfig();
    private final UnitOfWorkManager unitOfWorkManager = new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory());

    protected ProcessConfig processConfig = new StaticProcessConfig(workItemHandlerConfig, processEventListenerConfig, unitOfWorkManager);

    protected RuleConfig ruleConfig = null;

    @Override
    public ProcessConfig process() {
        return processConfig;
    }

    @Override
    public RuleConfig rule() {
        return ruleConfig;
    }
}
