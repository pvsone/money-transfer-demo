package io.temporal.samples.moneytransfer;

import io.temporal.api.common.v1.Payloads;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.common.converter.DataConverter;
import io.temporal.samples.moneytransfer.model.TransferInput;

import java.util.Optional;

public class TransferApprovals {

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
