# QueueGo CI Auto Recovery

## Purpose

The owner should not need to reopen ChatGPT whenever an Android or Web CI
failure occurs. A GitHub Actions monitor watches completed critical CI runs,
classifies actual failed-job logs, and continues with a bounded safe action.

## What works without manual intervention

1. The trusted default-branch workflow receives a GitHub `workflow_run`
   completed event. An hourly sweep catches missed events.
2. On **every** completed tracked workflow (green OR red), it immediately
   queries all the latest watched workflow runs sharing the **same branch and
   source SHA**. One successful check never ends the verification of the other
   checks. Completed failed siblings are triaged in the same job. In-progress
   siblings remain running, with their own completion event triggering another
   pass. No hourly wait is needed for the normal path.
3. It rejects third-party fork PRs, stale branch SHAs, unknown workflow names,
   and unfinished builds.
4. It checks failed-job logs for clear infrastructure failures such as HTTP 503
   or a dropped network connection.
5. For **proven transient failures only**, it requests **one retry** of failed
   jobs. An issue marker prevents even a repeated completion event or schedule
   sweep from requesting a second retry of the same attempt. GitHub runs
   downstream jobs only when their declared dependencies pass.
6. For assertions, Kotlin compiler errors, policy failures, and unknown
   failures, it creates a deduplicated GitHub Issue with source SHA, job
   evidence and link. It never blindly reruns broken source indefinitely.
7. A subsequent successful run on the same current source closes the Issue.

CI source code stays untouched. There is **no automatic merge**, release,
APK distribution, database migration, or privileged operation on code checked
out from a PR.

## What still requires a code-writing agent

This monitor does **not** have an autonomous coding model or unattended access
to OpenAI/GitHub Copilot credentials. A deterministic source error will be
recorded for a developer or separately connected coding agent; it will
**not** be fixed merely by rerunning a test. Fully unattended code changes
require configuring such an agent with narrow branch/PR-only permissions,
review controls and spending limits. Never give such an agent Production
Supabase service-role credentials or Android release signing secrets.

## Configuration and controls

- Workflow: `.github/workflows/queuego-ci-autorecovery.yml`
- Implementation: `ops/ci/queuego_autorecovery.py`
- Offline tests: `python3 tests/test_queuego_ci_autorecovery.py`
- GitHub token scopes: `contents: read`, `actions: write`, `issues: write`;
  only the trusted triage job has write permissions. PR validation runs read-only.
- Safety: one transient re-run maximum (first attempt only); no auto-retry of
  source/compiler/test failures, fork PRs, or stale branch heads.
- For a manual dry run: GitHub Actions > QueueGo CI Auto Recovery > Run
  workflow; provide the exact run ID and keep dry_run enabled.
- To temporarily suspend: disable this specific GitHub Actions workflow.
- The workflow must be merged into `main` to observe other CI runs. Creating
  it on a PR branch alone does not activate `workflow_run` or schedules.
- **Hourly scheduling is only a missed-event fallback**, not the normal
  verification cadence. GitHub's normal `workflow_run` event fires on completion
  of each tracked workflow (there may still be GitHub runner queue latency).
- Source-compile or test failures are triaged immediately but still require a
  separately authorized code-writing agent to patch code. Monitoring does not
  grant the monitor itself autonomous code-editing capability.

## Recovery expectations

Failures from unconfigured Firebase, unavailable devices, release-signing
secrets, 3rd-party APIs and real-device test requirements remain blocked by
the appropriate gates. Issues describe the blocker without claiming the app
is Production-ready.

Do not confuse a green source contract with physical Android app evidence.
Only exact-HEAD CI + relevant real-device runtime gates permit final APK
release review.
