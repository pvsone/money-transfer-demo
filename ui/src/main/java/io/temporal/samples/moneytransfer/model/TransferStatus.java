package io.temporal.samples.moneytransfer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferStatus {
    private int progressPercentage;
    private String transferState;
    private String workflowStatus;
    private ChargeResponse chargeResult;
    private int approvalTime;
    private TransferInput input;
}
