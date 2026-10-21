package io.temporal.samples.moneytransfer.web;

import io.javalin.Javalin;
import io.temporal.samples.moneytransfer.helper.ServerInfo;
import io.temporal.samples.moneytransfer.model.*;

import java.util.AbstractMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.temporal.samples.moneytransfer.TransferApprovals.listPendingApprovals;
import static io.temporal.samples.moneytransfer.TransferLister.listWorkflows;
import static io.temporal.samples.moneytransfer.TransferRequester.*;
import static io.temporal.samples.moneytransfer.TransferScheduler.runSchedule;

public class WebServer {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "svelte_ui";
            });
        });

        app.get("/serverinfo", ctx -> {
            ctx.json(ServerInfo.getServerInfo());
        });

        app.post("/runWorkflow", ctx -> {
            UXParameters uxParameters = ctx.bodyAsClass(UXParameters.class);
            TransferInput transferInput = uxParameters.toTransferInput();
            String transferId = runWorkflow(transferInput, uxParameters.getScenario());
            ctx.json(new AbstractMap.SimpleEntry<>("transferId", transferId));
        });

        app.post("/scheduleWorkflow", ctx -> {
            ScheduleParameters scheduleParameters = ctx.bodyAsClass(ScheduleParameters.class);
            String transferId = runSchedule(scheduleParameters);
            ctx.json(new AbstractMap.SimpleEntry<>("transferId", transferId));
        });

        app.post("/runQuery", ctx -> {
            // get workflowId from request POST body
            WorkflowId workflowIdObj = ctx.bodyAsClass(WorkflowId.class);
            String workflowId = workflowIdObj.getWorkflowId();
            TransferStatus transferState = runQuery(workflowId);
            ctx.json(transferState);
        });

        app.post("/getWorkflowOutcome", ctx -> {
            if (ctx.formParam("workflowId") == null) {
                ctx.json(new AbstractMap.SimpleEntry<>("message", "workflowId is required"));
                return;
            }

            // get workflowId from request POST body
            String workflowId = ctx.formParam("workflowId");
            TransferOutput workflowOutcome = getWorkflowOutcome(workflowId);
            ctx.json(workflowOutcome);
        });

        app.get("/listWorkflows", ctx -> {
            List<WorkflowStatus> workflowList = listWorkflows();
            ctx.json(workflowList);
        });

        app.get("/pendingApprovals", ctx -> {
            ctx.json(listPendingApprovals());
        });

        app.get("/test", ctx -> ctx.result("Hello Javalin!"));

        app.get("/simulateDelay", ctx -> {
            String seconds_param = ctx.queryParam("s");
            if (seconds_param != null) {
                int seconds = Integer.parseInt(seconds_param);
                System.out.println("Simulating API response delay: " + seconds);
                TimeUnit.SECONDS.sleep(seconds);
                ctx.result("Delay finished after " + seconds + " seconds");
            } else {
                ctx.result("use query param s to specify seconds to delay");
            }
        });

        app.post("/approveTransfer", ctx -> {
            // get workflowId from request POST body
            WorkflowId workflowIdObj = ctx.bodyAsClass(WorkflowId.class);
            String workflowId = workflowIdObj.getWorkflowId();
            runApproveSignal(workflowId);
            ctx.result("{\"signal\": \"sent\"}");
        });

        app.start(7070);
    }
}
