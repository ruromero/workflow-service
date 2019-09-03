package org.kiegroup.kogito.serverless.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.kie.kogito.Application;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.services.uow.UnitOfWorkExecutor;
import org.kiegroup.kogito.serverless.model.JsonModel;
import org.kiegroup.kogito.serverless.process.JsonProcess;

@Path("/process")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProcessResource {

    @Inject
    Application application;

    @POST
    public CompletionStage<JsonObject> createInstance(JsonObject data) {
        return CompletableFuture.supplyAsync(() -> execute(data));
    }

    private JsonObject execute(JsonObject data) {
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            ProcessInstance<JsonModel> pi = (ProcessInstance<JsonModel>) application.processes().processById(JsonProcess.PROCESS_ID).createInstance(JsonModel.newInstance(data));
            pi.start();
            if (pi.status() == org.kie.api.runtime.process.ProcessInstance.STATE_ERROR && pi.error().isPresent()) {
                throw new ProcessInstanceExecutionException(pi.id(), pi.error().get().failedNodeId(), pi.error().get().errorMessage());
            }
            return pi.variables().getData();
        });
    }
}
