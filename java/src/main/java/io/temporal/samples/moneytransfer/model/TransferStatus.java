package io.temporal.samples.moneytransfer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("chargeResult")
    private DepositResponse depositResponse;

    private int approvalTime;
    private TransferInput input;
}
