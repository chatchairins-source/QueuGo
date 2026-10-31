#!/usr/bin/env python3
"""QueueGo CI failure monitor. Trusted default-branch runtime only.

Can open/close issues and request ONE retry for positively identified transient
failures. Cannot modify source code, merge, deploy, or execute failed PR code.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

WATCHED = {
    "QueueGo Native Android Pilot",
    "QueueGo Export Tested Country APKs",
    "QueueGo Native POS CI",
    "QueueGo Installed Country Offline Runtime",
    "QueueGo Rider Android Authenticated Review",
    "QueueGo RC Tests",
    "QueueGo Map Engine Buriram",
    "Customer P0 Android runtime matrix",
    "Native Merchant Grocery verification",
}
TRANSIENT = re.compile(
    r"(?i)(connection reset by peer|temporary failure in name resolution|"
    r"could not resolve host|HTTP[ /]*(429|502|503|504)\b|"
    r"rate limit exceeded|service unavailable|runner lost communication|"
    r"connection timed out|TLS handshake timeout)"
)
CODE_ERROR = re.compile(
    r"(?i)(AssertionError|ERR_ASSERTION|compilation error|unresolved reference|"
    r"e: file:///|> Task .* FAILED|failed to compile|syntaxerror|"
    r"cannot find symbol|permission denied|403 forbidden|missing.*secret)"
)
MATCH_ERROR = re.compile(
    r"(?i)(assertion|error:|FAILURE:|failed|exception|unresolved|timed out|"
    r"connection reset|could not resolve|rate limit)"
)
TITLE_PREFIX = "[QueueGo CI Recovery]"


def classify(logs):
    if CODE_ERROR.search(logs):
        return "code-or-policy"
    return "transient" if TRANSIENT.search(logs) else "unknown"


def excerpt(logs):
    lines = []
    for line in logs.splitlines():
        if not MATCH_ERROR.search(line):
            continue
        line = re.sub(r"(?i)(github_pat_|gh[pousr]_)[A-Za-z0-9_]+", "[REDACTED]", line)
        line = re.sub(r"(?i)(authorization:\s*(bearer|token)\s+)\S+", r"\1[REDACTED]", line)
        line = re.sub(r"https?://\S+", "[URL]", line)
        line = re.sub(r"(?i)(password|secret|token|api[_-]?key)\s*[:=]\s*\S+", r"\1=[REDACTED]", line)
        line = line.strip()[:220]
        if line and line not in lines:
            lines.append(line)
        if len(lines) == 12:
            break
    return "\n".join(lines)[:2400] or "Read the original GitHub job log for the exact failure."


class API:
    def __init__(self, repo, token):
        self.repo = repo
        self.token = token
        self.root = "https://api.github.com/repos/" + repo

    def call(self, method, path, body=None, text=False):
        headers = {
            "Authorization": "Bearer " + self.token,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "queuego-ci-recovery",
        }
        raw_body = None
        if body is not None:
            raw_body = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.root + path, raw_body, headers, method=method)
        with urllib.request.urlopen(req, timeout=25) as res:
            data = res.read(2_000_000)
        return data.decode("utf-8", "replace") if text else (json.loads(data) if data else {})

    def get(self, path): return self.call("GET", path)
    def post(self, path, body=None): return self.call("POST", path, body or {})
    def patch(self, path, body): return self.call("PATCH", path, body)

    def issue(self, title):
        for page in range(1, 4):
            rows = self.get("/issues?state=open&per_page=100&page=" + str(page))
            for row in rows:
                if "pull_request" not in row and row.get("title") == title:
                    return row
            if len(rows) < 100:
                break
        return None


def trusted(run, repo):
    source = run.get("head_repository") or {}
    return (
        run.get("name") in WATCHED
        and source.get("full_name", "").lower() == repo.lower()
        and run.get("event") in ("push", "pull_request", "workflow_dispatch")
        and run.get("status") == "completed"
        and bool(run.get("head_sha"))
    )


def live_head(gh, run):
    branch = run.get("head_branch")
    if not branch:
        return False
    try:
        data = gh.get("/branches/" + urllib.parse.quote(branch, safe=""))
        return data.get("commit", {}).get("sha") == run["head_sha"]
    except (urllib.error.HTTPError, urllib.error.URLError):
        return False


def failed_jobs(gh, rid):
    jobs = []
    for page in (1, 2):
        rows = gh.get("/actions/runs/%d/jobs?per_page=100&page=%d" % (rid, page)).get("jobs", [])
        jobs += [j for j in rows if j.get("conclusion") in ("failure", "timed_out")]
        if len(rows) < 100:
            break
    return jobs[:8]


def inspect(gh, rid):
    jobs = failed_jobs(gh, rid)
    chunks = []
    for job in jobs[:3]:
        try:
            chunks.append(gh.call("GET", "/actions/jobs/%d/logs" % job["id"], text=True))
        except (urllib.error.HTTPError, urllib.error.URLError):
            chunks.append("")
    modes = [classify(chunk) for chunk in chunks]
    if jobs and len(jobs) <= 3 and modes and all(m == "transient" for m in modes):
        kind = "transient"
    elif "code-or-policy" in modes:
        kind = "code-or-policy"
    else:
        kind = "unknown"
    return kind, jobs, excerpt("\n".join(chunks))


def comment_once(gh, issue, marker, body):
    if marker in (issue.get("body") or ""):
        return
    rows = gh.get("/issues/%d/comments?per_page=100" % issue["number"])
    if any(marker in (c.get("body") or "") for c in rows):
        return
    gh.post("/issues/%d/comments" % issue["number"], {"body": body + "\n\n" + marker})


def process(gh, rid, dry=False):
    run = gh.get("/actions/runs/%d" % rid)
    if not trusted(run, gh.repo):
        print("SKIP untrusted or unfinished run", rid)
        return
    if not live_head(gh, run):
        print("SKIP stale branch HEAD", rid)
        return

    title = "%s %s @ %s" % (TITLE_PREFIX, run["name"], run["head_sha"][:10])
    issue = gh.issue(title)
    url = run.get("html_url", "https://github.com/%s/actions/runs/%d" % (gh.repo, rid))
    attempt = int(run.get("run_attempt", 1))
    marker = "<!-- ci-recovery-run:%s-attempt:%s -->" % (rid, attempt)

    if run.get("conclusion") == "success":
        if issue:
            print("Resolved issue", issue["number"])
            if not dry:
                comment_once(gh, issue, marker, "CI passed for exact source: " + url)
                gh.patch("/issues/%d" % issue["number"], {"state": "closed", "state_reason": "completed"})
        return
    if run.get("conclusion") not in ("failure", "timed_out"):
        return

    mode, jobs, snippet = inspect(gh, rid)
    retry = mode == "transient" and attempt == 1 and bool(jobs)
    action = "One safe retry of failed jobs" if retry else "Human or authorized code-agent fix required"
    body = (
        "Automatic QueueGo CI triage (no production changes).\n\n"
        "Workflow: %s\n\nBranch: %s\n\nSource SHA: %s\n\n"
        "Run: %s\n\nAttempt: %d\n\nCause: %s\n\nNext action: %s\n\n"
        "Failed-job evidence (sanitized):\n\n    %s\n\n"
        "Fix on a branch, run all CI, and review before any merge/deployment.\n\n%s"
    ) % (run["name"], run.get("head_branch"), run["head_sha"], url,
         attempt, mode, action, snippet.replace("\n", "\n    "), marker)
    print("TRIAGE", rid, mode, action)
    if dry:
        print(body[:1800])
        return
    if not issue:
        issue = gh.post("/issues", {"title": title, "body": body})
        print("Created issue", issue["number"])
    else:
        comment_once(gh, issue, marker, body)
        print("Updated issue", issue["number"])

    if retry:
        try:
            gh.post("/actions/runs/%d/rerun-failed-jobs" % rid)
            print("GitHub accepted bounded retry", rid)
        except urllib.error.HTTPError as error:
            print("Retry unavailable: HTTP", error.code)


def main():
    gh = API(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_TOKEN"])
    dry = os.getenv("QG_RECOVERY_DRY_RUN", "false").lower() == "true"
    event = os.environ.get("GITHUB_EVENT_NAME")
    if event == "workflow_run":
        with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as infile:
            rid = int(json.load(infile)["workflow_run"]["id"])
        process(gh, rid, dry)
    elif event == "workflow_dispatch":
        process(gh, int(os.environ["QG_SOURCE_RUN_ID"]), dry)
    elif event == "schedule":
        runs = gh.get("/actions/runs?status=completed&per_page=50").get("workflow_runs", [])
        cutoff = datetime.now(timezone.utc) - timedelta(hours=18)
        for run in runs:
            when = datetime.fromisoformat(run["updated_at"].replace("Z", "+00:00"))
            if when >= cutoff and run.get("name") in WATCHED:
                process(gh, run["id"], dry)
    else:
        sys.exit("Unsupported CI monitor trigger")


if __name__ == "__main__":
    main()
