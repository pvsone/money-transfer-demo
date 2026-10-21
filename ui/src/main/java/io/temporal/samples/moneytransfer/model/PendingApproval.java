package io.temporal.samples.moneytransfer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingApproval {
    private String workflowId;
    private int amount;
    private String fromAccount;
    private String toAccount;
    private String approver;
}
