# Approvals Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/approvals.html` page to the Java web UI that lists every running transfer awaiting approval and lets the user approve each one.

**Architecture:** A new `TransferApprovals` class finds pending transfers with a visibility query on the `Step` search attribute and decodes each one's `TransferInput` from its start event in history. `WebServer` exposes the list as `GET /pendingApprovals`. A plain HTML/JS page polls that endpoint and approves through the existing `POST /approveTransfer`.

**Tech Stack:** Java 17, Temporal Java SDK 1.35.0, Javalin 5.6.5, Lombok, JUnit 5 (new, test-only), and vanilla HTML/JS.

**Spec:** `docs/superpowers/specs/2026-10-21-approvals-dashboard-design.md`

## Global Constraints

- Change files under `ui/` only. Do not touch workflow or worker code in any SDK directory.
- Do not modify the compiled Svelte bundle under `ui/src/main/resources/svelte_ui/_app/`.
- Selection query, exactly: `Step = 'Awaiting_Approval' AND ExecutionStatus = 'Running'`.
- Approver mapping: `"Checking"` → `"Alice"`, `"Savings"` → `"Bob"`, anything else (including null) → `"—"`.
- No requestor column and no Reject action.
- The page polls every 2 seconds, because unapproved transfers fail after 30s.
- Paths below are relative to `ui/`. The Java package root is `src/main/java/io/temporal/samples/moneytransfer/`, abbreviated `PKG/`.

## Review Focus

1. **Untrusted text in the table.** Workflow IDs and account names must be rendered with `textContent`, never `innerHTML`, so a crafted account string can't inject markup. (Task 3)
2. **Double-clicking Approve.** The button disables on the first click and stays disabled until the next re-render, so only one signal is sent per click. (Task 3)
3. **Backend or network failure while polling.** The page shows a one-line error and keeps polling. It doesn't throw or stop the interval. (Task 3)
4. **A transfer finishes between the list call and the history fetch.** That execution is logged and skipped; the rest of the list is still returned. (Task 2)
5. **A start event with no input, or input that doesn't decode.** The decode helper throws, so the listing code skips that execution. (Task 1 test `startInputThrowsWhenNoInput`)

---

### Task 1: Model, approver mapping, and start-input decoding

**Files:**
- Modify: `build.gradle` (add test deps)
- Create: `PKG/model/PendingApproval.java`
- Create: `PKG/TransferApprovals.java` (helpers only in this task)
- Test: `src/test/java/io/temporal/samples/moneytransfer/TransferApprovalsTest.java`

**Interfaces:**
- Produces:
  - `PendingApproval` (Lombok `@Data @NoArgsConstructor @AllArgsConstructor`), fields in this order: `String workflowId, int amount, String fromAccount, String toAccount, String approver`.
  - `static String TransferApprovals.approverFor(String fromAccount)`
  - `static TransferInput TransferApprovals.startInput(WorkflowExecutionHistory history, DataConverter dataConverter)`: decodes the first event's `WorkflowExecutionStartedEventAttributes.getInput()` as `TransferInput`. Throws `IllegalStateException` when the first event isn't a start event or has no input.

- [ ] **Step 1: Add test dependencies to `build.gradle`**

```groovy
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
testCompileOnly 'org.projectlombok:lombok:1.18.48'
testAnnotationProcessor 'org.projectlombok:lombok:1.18.48'
```

- [ ] **Step 2: Write the failing tests**

In `TransferApprovalsTest`:
- `approverForChecking`: `assertEquals("Alice", approverFor("Checking"))`
- `approverForSavings`: `assertEquals("Bob", approverFor("Savings"))`
- `approverForOther`: `assertEquals("—", approverFor("Brokerage"))` and `assertEquals("—", approverFor(null))`
- `startInputDecodesTransferInput`: build a `History` holding one `HistoryEvent` with `EventType.EVENT_TYPE_WORKFLOW_EXECUTION_STARTED`. Its `WorkflowExecutionStartedEventAttributes` input is `DefaultDataConverter.newDefaultInstance().toPayloads(new TransferInput(100, "Checking", "Savings")).get()`. Wrap it as `new WorkflowExecutionHistory(history)`. Assert that `startInput(...)` with the same converter equals `new TransferInput(100, "Checking", "Savings")`.
- `startInputThrowsWhenNoInput`: use the same start event without input, and `assertThrows(IllegalStateException.class, ...)`.

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `./gradlew test`
Expected: compilation fails because `TransferApprovals` and `PendingApproval` don't exist.

- [ ] **Step 4: Implement `PendingApproval`, `approverFor`, and `startInput` per the Interfaces above**

For `startInput`, call `dataConverter.fromPayloads(0, Optional.of(payloads), TransferInput.class, TransferInput.class)`.

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/java/io/temporal/samples/moneytransfer/model/PendingApproval.java src/main/java/io/temporal/samples/moneytransfer/TransferApprovals.java src/test
git commit -m "Add approver mapping and start-input decoding for approvals dashboard"
```

### Task 2: Pending-approvals listing and endpoint

**Files:**
- Modify: `PKG/TransferApprovals.java`
- Modify: `PKG/web/WebServer.java` (add the route next to `/listWorkflows`)

**Interfaces:**
- Consumes: `approverFor`, `startInput`, and `PendingApproval` from Task 1, plus `TemporalClient.get()`.
- Produces:
  - `public static List<PendingApproval> TransferApprovals.listPendingApprovals() throws FileNotFoundException, SSLException`
  - `GET /pendingApprovals`, which returns a JSON array of `{workflowId, amount, fromAccount, toAccount, approver}`.

- [ ] **Step 1: Implement `listPendingApprovals()`**

The flow:
1. Get the client from `TemporalClient.get()`.
2. Run `client.listExecutions(<selection query from Global Constraints>)`.
3. For each execution, call `client.fetchHistory(workflowId)` and decode it with `startInput(history, client.getOptions().getDataConverter())`. Then build a `PendingApproval` with `approverFor(input.getFromAccount())`.
4. Wrap the per-execution work in a try/catch. On an exception, print `Skipping <workflowId>: <e>` with `System.out.println` (as `TransferRequester` does) and continue.

The listing isn't unit-tested, because the test server doesn't support custom search-attribute queries. Task 3 covers it end to end.

- [ ] **Step 2: Add the route**

Add `app.get("/pendingApprovals", ctx -> ctx.json(listPendingApprovals()));` with a static import.

- [ ] **Step 3: Verify that it builds and the endpoint responds**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

With a local Temporal server and the UI running (`./startlocalwebui.sh`), run `curl -s localhost:7070/pendingApprovals`.
Expected: `[]` when nothing is pending.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/temporal/samples/moneytransfer/TransferApprovals.java src/main/java/io/temporal/samples/moneytransfer/web/WebServer.java
git commit -m "Add /pendingApprovals endpoint"
```

### Task 3: Approvals page and README

**Files:**
- Create: `src/main/resources/svelte_ui/approvals.html`
- Modify: `README.md` (append to the "Require Human-In-Loop Approval" section, around line 57)

**Interfaces:**
- Consumes: `GET /pendingApprovals` from Task 2, and the existing `POST /approveTransfer` with body `{"workflowId": "<id>"}` (JSON, `Content-Type: application/json`).

- [ ] **Step 1: Write `approvals.html`**

Use the full-document skeleton (`<!doctype html>`, charset, viewport). The page contains:
- A link to `./_app/immutable/assets/0.cf0b3f4a.css`, plus a few inline table styles.
- An `<h1>Pending Approvals</h1>` heading and an `<a href="/">← Back to Transfers</a>` link.
- A table with columns **Transfer ID | From Account | To Account | Amount | Approver | Action**. Format the amount as `$<amount>`.

Behavior:
- **Polling:** `refresh()` fetches `/pendingApprovals` on load and then every 2000 ms.
- **Empty list:** show a single row reading `No transfers awaiting approval.`
- **Fetch error:** show `Unable to load pending approvals.` and keep the interval running.
- **Rendering:** build the cells with `document.createElement` and `textContent` (Review Focus 1).
- **Approve button:** on click, set `disabled = true`, POST to `/approveTransfer`, then call `refresh()` (Review Focus 2).

- [ ] **Step 2: Add the README note**

Add an **Approvals Dashboard** subsection covering:
- The URL: `http://localhost:7070/approvals.html`.
- What it lists: running transfers whose `Step` search attribute is `Awaiting_Approval`. Only the Java worker sets this.
- The Alice/Bob approver mapping.

- [ ] **Step 3: Verify end to end**

Prerequisites:
- A running local Temporal server with the `Step` search attribute created.
- The Java worker running (`java/`).
- The UI running (`./startlocalwebui.sh`).

Checks:
1. Start two "Require Human-In-Loop Approval" transfers, one from Checking and one from Savings. Open `/approvals.html`. Expected: both rows appear within about 2s, with the correct amount and accounts and Approver set to Alice or Bob.
2. Click Approve on one row. Expected: the button disables, the row disappears within a couple of refreshes, and that transfer completes in the main UI.
3. Leave the other row alone. Expected: it drops off after about 30s, when the workflow fails.
4. Start a Happy Path transfer. Expected: it never appears.
5. Stop the UI server while the page is open. Expected: the error line shows. Restart the server. Expected: the page recovers on the next poll.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/svelte_ui/approvals.html README.md
git commit -m "Add approvals dashboard page"
```
