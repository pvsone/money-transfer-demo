# Approvals Dashboard (UI-only)

## Context
The Human-In-Loop scenario leaves a transfer waiting on an `approveTransfer` signal, and right now the only way to approve one is from that transfer's own progress view or from the CLI. We want a separate page that lists **every** transfer waiting on approval, with an Approve button on each row.

Agreed scope:
- **Columns:** Transfer ID, From Account, To Account, Amount, Approver, and an Approve button.
- **Approver** is hardcoded: `Checking` gives **Alice**, `Savings` gives **Bob**, and any other value shows `—`.
- **No requestor and no Reject.**
- **Changes stay in `ui/` only.** No changes to workflow or worker code in any SDK.
- **Standalone static page** served by Javalin at `/approvals.html`. The compiled Svelte bundle stays untouched; its source isn't in the repo.

Selection: a transfer is listed when its `Step` search attribute equals `Awaiting_Approval` and it is still running. Today only the Java worker sets this value (`java/.../AccountTransferWorkflowScenarios.java:55`). The other six SDKs upsert `Step` only for Validate, Withdraw, Deposit and Notify, so their pending transfers will **not** appear. That follows from the UI-only scope.

Constraint: HIL workflows fail after 30s without approval (`approvalTime = 30`). The page therefore auto-refreshes so rows appear and disappear quickly.

## Design

### Backend (Java, `ui/src/main/java/io/temporal/samples/moneytransfer/`)
1. **New model `model/PendingApproval.java`** (Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, same as `TransferInput`). Fields: `workflowId`, `amount`, `fromAccount`, `toAccount`, `approver`.
2. **New `TransferApprovals.java`** with `listPendingApprovals()`, structured like `TransferLister`:
   - Run `client.listExecutions("Step = 'Awaiting_Approval' AND ExecutionStatus = 'Running'")` with `TemporalClient.get()`. The `Step` search attribute alone decides which transfers appear, so no per-workflow status query is needed.
   - **Read the input from history.** Call `client.fetchHistory(workflowId)`, take the first event's `WorkflowExecutionStartedEventAttributes.getInput()`, and decode it to `TransferInput` with `client.getOptions().getDataConverter()`. That converter respects the `CryptCodec` setup in `TemporalClient`.
   - Set the approver with a small static helper, `approverFor(fromAccount)`.
   - If the history fetch or decode throws for an execution (for example, it completed between the list call and the fetch), log it and skip it.
3. **`web/WebServer.java`:** add `app.get("/pendingApprovals", ctx -> ctx.json(listPendingApprovals()))`. Approve reuses the existing `POST /approveTransfer` with `{workflowId}`; nothing new is needed there.

### Frontend: `ui/src/main/resources/svelte_ui/approvals.html`
- A single self-contained HTML file with inline JS and no build step.
- Links the existing bundle stylesheet `./_app/immutable/assets/0.cf0b3f4a.css` so it matches the main UI, plus a few inline table styles.
- Header "Pending Approvals" and a "← Back to Transfers" link to `/`.
- `fetch('/pendingApprovals')` runs on load and every 2s, then re-renders the table. When nothing is pending, the table shows "No transfers awaiting approval."
- Approve button: disable the button, `POST /approveTransfer` with `{"workflowId": ...}`, then refresh right away.

### Docs
- Add a short "Approvals Dashboard" note to the Human-In-Loop section of `ui/README.md`. It covers the URL and the Alice/Bob mapping.

## Files
- New: `model/PendingApproval.java`, `TransferApprovals.java`, `svelte_ui/approvals.html`
- Modified: `web/WebServer.java`, `ui/README.md`
- Reused: `TemporalClient.get()`, the `listExecutions` pattern from `TransferLister`, the existing `/approveTransfer` endpoint and `runApproveSignal`.

## Verification
1. `cd ui && ./gradlew build` compiles cleanly.
2. Start a local server (`./starttemporalserver.sh`), the Java worker, and the UI (`./startlocalwebui.sh`).
3. In the main UI, start two Human-In-Loop transfers, one from Checking and one from Savings. Open `http://localhost:7070/approvals.html`. Both rows should appear with the correct amount and accounts, and Approver set to Alice or Bob.
4. Click Approve on one row within 30s. The row disappears, and that transfer completes in the main UI and the Temporal UI. The other row times out after 30s and drops off.
5. Start a Happy Path transfer and confirm it never appears on the page.
6. The `Step` search attribute must exist (`temporal operator search-attribute create --name Step --type Keyword`). It is already listed in the README setup.

