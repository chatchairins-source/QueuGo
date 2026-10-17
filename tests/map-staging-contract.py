#!/usr/bin/env python3
"""Contract tests for the staging runner. No cloud host or Production environment is used."""
import json,subprocess,tempfile
from pathlib import Path

SCRIPT=Path("ops/maps/stage_container.py")
text=SCRIPT.read_text()
checks=0

def ok(value,message):
    global checks
    assert value,message
    checks+=1

for required in [
    '"--publish",f"127.0.0.1:{port}:8765"',
    '"--read-only"',
    '"--cap-drop","ALL"',
    '"--security-opt","no-new-privileges:true"',
    '"--memory",DEFAULT_MEMORY',
    '"--pids-limit",DEFAULT_PIDS',
    'f"{store}:/data:ro"',
    'reverse_proxy_required',
    'public_bind',
]:
    ok(required in text,f"missing staging safety contract: {required}")
ok("0.0.0.0" not in text,"staging helper must never publish the engine on all interfaces")
ok("--replace" in text,"replacement must require an explicit operator flag")
ok("resolve(store,runtime)" in text,"active package must be checksum-verified before launch")

subprocess.run(["python","-m","py_compile",str(SCRIPT)],check=True)
checks+=1

print(json.dumps({"checks":checks,"failures":0,"scope":"QueueGo Map staging container safety contract"}))
