package io.temporal.samples.moneytransfer;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.History;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.history.v1.WorkflowExecutionStartedEventAttributes;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.samples.moneytransfer.model.TransferInput;
import org.junit.jupiter.api.Test;

import static io.temporal.samples.moneytransfer.TransferApprovals.approverFor;
import static io.temporal.samples.moneytransfer.TransferApprovals.startInput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferApprovalsTest {

    private static final DataConverter DATA_CONVERTER = DefaultDataConverter.newDefaultInstance();

    @Test
    void approverForChecking() {
        assertEquals("Alice", approverFor("Checking"));
    }

    @Test
    void approverForSavings() {
        assertEquals("Bob", approverFor("Savings"));
    }

    @Test
    void approverForOther() {
        assertEquals("—", approverFor("Brokerage"));
        assertEquals("—", approverFor(null));
    }

    @Test
    void startInputDecodesTransferInput() {
        TransferInput expected = new TransferInput(100, "Checking", "Savings");
        WorkflowExecutionStartedEventAttributes attributes = WorkflowExecutionStartedEventAttributes.newBuilder()
                .setInput(DATA_CONVERTER.toPayloads(expected).get())
                .build();

        assertEquals(expected, startInput(historyWith(attributes), DATA_CONVERTER));
    }

    @Test
    void startInputThrowsWhenNoInput() {
        WorkflowExecutionStartedEventAttributes attributes = WorkflowExecutionStartedEventAttributes.newBuilder().build();

        assertThrows(IllegalStateException.class, () -> startInput(historyWith(attributes), DATA_CONVERTER));
    }

    private static WorkflowExecutionHistory historyWith(WorkflowExecutionStartedEventAttributes attributes) {
        HistoryEvent started = HistoryEvent.newBuilder()
                .setEventId(1)
                .setEventType(EventType.EVENT_TYPE_WORKFLOW_EXECUTION_STARTED)
                .setWorkflowExecutionStartedEventAttributes(attributes)
                .build();
        return new WorkflowExecutionHistory(History.newBuilder().addEvents(started).build());
    }
}
