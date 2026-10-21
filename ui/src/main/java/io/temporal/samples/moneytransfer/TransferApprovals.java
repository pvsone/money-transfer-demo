package io.temporal.samples.moneytransfer;

import io.temporal.api.common.v1.Payloads;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionMetadata;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.common.converter.DataConverter;
import io.temporal.samples.moneytransfer.model.PendingApproval;
import io.temporal.samples.moneytransfer.model.TransferInput;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransferApprovals {

    public static List<PendingApproval> listPendingApprovals() throws FileNotFoundException, SSLException {
        WorkflowClient client = TemporalClient.get();

        List<WorkflowExecutionMetadata> executions = client.listExecutions(
                "Step = 'Awaiting_Approval' AND ExecutionStatus = 'Running'"
            ).toList();

        List<PendingApproval> pendingApprovals = new ArrayList<>();

        for (WorkflowExecutionMetadata execution : executions) {
            String workflowId = execution.getExecution().getWorkflowId();
            try {
                TransferInput input = startInput(client.fetchHistory(workflowId), client.getOptions().getDataConverter());
                pendingApprovals.add(new PendingApproval(
                        workflowId,
                        input.getAmount(),
                        input.getFromAccount(),
                        input.getToAccount(),
                        approverFor(input.getFromAccount())
                ));
            } catch (Exception e) {
                // e.g. the transfer completed between the list call and the history fetch
                System.out.println("Skipping " + workflowId + ": " + e);
            }
        }

        return pendingApprovals;
    }

    public static String approverFor(String fromAccount) {
        if ("Checking".equals(fromAccount)) {
            return "Alice";
        }
        if ("Savings".equals(fromAccount)) {
            return "Bob";
        }
        return "—";
    }

    // the transfer input is read from the start event, so this works for any SDK's worker
    public static TransferInput startInput(WorkflowExecutionHistory history, DataConverter dataConverter) {
        HistoryEvent firstEvent = history.getHistory().getEvents(0);
        if (!firstEvent.hasWorkflowExecutionStartedEventAttributes()
                || !firstEvent.getWorkflowExecutionStartedEventAttributes().hasInput()) {
            throw new IllegalStateException("Start event has no input");
        }

        Payloads payloads = firstEvent.getWorkflowExecutionStartedEventAttributes().getInput();
        return dataConverter.fromPayloads(0, Optional.of(payloads), TransferInput.class, TransferInput.class);
    }
}
