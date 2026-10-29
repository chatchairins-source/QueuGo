# QueueGo Work Continuity Workflow

This file is the authoritative operating procedure for continuing QueueGo work safely across ChatGPT/Work stream interruptions.

Repository: `chatchairins-source/QueuGo`

## Mandatory startup sequence

Every new work session MUST begin in this order:

1. Read `WORKFLOW.md`.
2. Read `CHECKLIST.md`.
3. Fetch current `main`.
4. Fetch relevant open PRs, working branches, successor branches and recent commits.
5. Determine the newest real descendant HEAD for the requested workstream.
6. Read recent commit history/checkpoints on that successor.
7. Resume only from the recorded `NEXT` item.

Conversation memory is not a Source of Truth. The repository is.

## Source-of-Truth and successor rules

- Use the newest real successor/descendant HEAD only.
- Never restart from an older implementation when a newer successor exists.
- Never reset or revert newer work merely to simplify continuation.
- Never cherry-pick an older implementation over a newer successor.
- Never create a duplicate implementation of an existing capability.
- A still-open PR may be an ancestor or superseded. Open status alone does not make it active.
- Before modifying code, prove why the selected HEAD is the correct successor.

## Work chunk rules

Large work must be split into small, independently resumable chunks.

Each chunk should follow:

1. identify scope and dependency;
2. establish root cause/evidence before bug fixes;
3. implement one bounded change;
4. run the relevant test/build/gate;
5. commit immediately;
6. update `CHECKLIST.md`;
7. record the next action.

Do not accumulate a large uncommitted change set.

## Checkpoint rules

Before a major change, create or identify a build/test-capable checkpoint and record:

- workstream;
- branch;
- HEAD SHA;
- what is already complete;
- current evidence/test status;
- known blockers;
- exact NEXT action.

After every completed chunk, commit before starting the next chunk.

## Required checklist statuses

Use only these execution statuses:

- `DONE` — implementation for the stated scope is committed and supported by relevant test/CI evidence.
- `IN PROGRESS` — work can continue in repository/tooling without owner-side physical action.
- `BLOCKED` — continuation requires something unavailable in the current environment or an explicit release/physical step.
- `NEXT` — the next bounded action to perform when that stream resumes.

A feature can be `DONE (source/test)` while still `BLOCKED (release)`. Do not collapse those into one status.

## Definition of done

Never report work as complete without both:

1. a concrete commit SHA for the completed scope; and
2. relevant test/build/CI evidence.

Passing unit/CI tests does NOT certify any item that requires:

- a physical Android/iOS device;
- real GPS or real-road driving;
- Production/public hosting;
- signed release AAB/APK;
- Play Console Closed Testing;
- Production database/payment/auth cutover;
- live external-provider approval;
- other owner-only or external release steps.

Those items must remain `BLOCKED` until actual evidence exists.

## Bug-fix evidence rule

Before fixing a bug, record the evidence-backed root cause.

Do not:
- guess the root cause and present it as fact;
- patch symptoms repeatedly without identifying ownership/state/lifecycle cause;
- declare a bug fixed solely because a static test passes when the reported bug requires physical/runtime evidence.

If the root cause cannot be established, say so and keep the task `IN PROGRESS` or `BLOCKED`.

## Stream interruption recovery

If ChatGPT/Work/connection interrupts:

1. discard conversational assumptions;
2. reopen `WORKFLOW.md`;
3. reopen `CHECKLIST.md`;
4. fetch current `main`;
5. fetch the workstream's newest successor HEAD;
6. inspect commits after the checklist checkpoint;
7. resume from the newest committed `NEXT`.

Never recreate work from memory when a repository checkpoint exists.

## Release and Production rules

- No mock/demo/fake data in Production.
- No Production database/order/payment/auth mutation merely to make a test pass.
- Respect explicit Deployment/Release Gates.
- Prefer web-first review for UI/features where the owner requested web approval before Android.
- Separate `source/test ready` from `ready to launch`.
- Preserve cash-only business rules unless a newer owner-approved Source of Truth changes them.

## Native Merchant POS table and payment safety

- Use the current Web Production POS on `main` as the parity oracle; reuse existing Production RPCs, RLS and permission checks.
- A cash payment is confirmed only by the server-authoritative `PAID` + `CLOSED` bill and matching method/tender. A read/refresh failure is not a payment failure and must never trigger an automatic second payment request.
- After an uncertain table-create or QR RPC response, read the current shop snapshot to reconcile; do not blindly repeat the write. Creating a table should immediately attempt its existing table-QR RPC and open the resulting server-backed QR.
- Do not delete a table that has POS order history. Explain that it must be edited/deactivated instead; preserve the existing shop-scoped table and QR functions.
- POS-only reports must use the existing Web Production dashboard/item-sales RPCs, include average ticket, cash sales, discounts, refunds, voids, comparison percentage, hourly rows and product sales, and export those same server-backed rows as UTF-8 CSV with spreadsheet-formula escaping. POS totals are dine-in + takeaway only; do not mix Delivery or GP into POS-only totals.
- Keep receipts tied to a confirmed paid bill and print through the existing HTTPS Print Bridge. Table QR PNG saving uses Android's document picker; QR printing uses the Android system print dialog to match Web `window.print`, without replacing or duplicating receipt printing. Do not alter Production money/ledger data for testing.

## Reporting after each chunk

Report only:

- Stream
- HEAD SHA
- Test/build/CI evidence
- Status
- NEXT

If a stream is blocked, state exactly what evidence/action is required to unblock it, then continue to another unblocked stream.

## Continuous execution

When the current platform/session permits continued execution, proceed from one completed chunk to the next without waiting for the owner to issue a separate command.

If the platform/session terminates, work cannot continue asynchronously by assumption. The repository checkpoints in this workflow are the recovery mechanism for the next session.

## Owner `ต่อ` continuation command

The owner command `ต่อ` means the previous work stream was interrupted. It is not a request to start a new task and requires no clarification.

On every `ต่อ`, immediately execute this recovery sequence:

1. Read `WORKFLOW.md`.
2. Read `CHECKLIST.md`.
3. Identify the latest workstream recorded in the checklist.
4. Fetch and verify the newest real successor/descendant HEAD for that workstream.
5. Inspect commit history after the recorded checkpoint.
6. Summarize recovered progress in no more than 3 lines and cite the relevant commit SHA(s).
7. Resume immediately from the recorded `NEXT` item.

Additional permanent rules for `ต่อ` and all QueueGo work:

- Never go back to an older implementation when newer committed work exists.
- Never start a new implementation for functionality already present in the successor line.
- Never repeat work that already has a commit checkpoint.
- Work only in small bounded chunks: fix -> test -> commit -> update `CHECKLIST.md` with status and exact `NEXT`.
- After every commit, update `NEXT` in `CHECKLIST.md` before relying on conversational context for continuation.
- Continue directly to the next unblocked chunk without waiting for another owner command when the current session/platform permits.
- Continue across all workstreams that can be completed in the current environment, in dependency/Release-Gate order.
- If a task requires owner action, a physical device, real GPS/road testing, signing credentials, Play Console, Production cutover, public hosting approval, or another unavailable external step, mark it `BLOCKED`, record exactly what the owner must do/provide, then continue with the next unblocked workstream.
- Never mark any scope `DONE` unless a commit SHA and relevant test/build/CI evidence both exist.
- `source/test ready` and `ready to launch` are separate states and must never be conflated.
