#!/usr/bin/env python3
"""Safely run the verified QueueGo Map package on an authorized staging host.

This helper never provisions infrastructure and never exposes the engine on a public
interface. It binds only 127.0.0.1 so a separately managed TLS reverse proxy can
publish the same-origin QueueGo Map service.
"""
import argparse,json,subprocess,tempfile,time,urllib.request
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from offline_package import resolve

DEFAULT_MEMORY="512m"
DEFAULT_CPUS="2"
DEFAULT_PIDS="256"

def ensure_verified_store(store):
    store=Path(store).resolve()
    if not store.is_dir():raise ValueError("Verified package store does not exist")
    with tempfile.TemporaryDirectory(prefix="queuego-map-stage-verify-") as runtime:
        files=resolve(store,runtime)
        active=json.loads((store/"active.json").read_text())["current"]
        return store,active,files

def command_for(store,tag,name,port):
    if not (1024<=port<=65535):raise ValueError("Host port must be 1024..65535")
    return [
        "docker","run","--detach",
        "--name",name,
        "--restart","unless-stopped",
        "--read-only",
        "--memory",DEFAULT_MEMORY,
        "--cpus",DEFAULT_CPUS,
        "--pids-limit",DEFAULT_PIDS,
        "--cap-drop","ALL",
        "--security-opt","no-new-privileges:true",
        "--tmpfs","/tmp:rw,noexec,nosuid,size=64m",
        "--publish",f"127.0.0.1:{port}:8765",
        "--volume",f"{store}:/data:ro",
        tag,
    ]

def container_exists(name):
    result=subprocess.run(["docker","inspect",name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    return result.returncode==0

def wait_health(port,timeout=45):
    url=f"http://127.0.0.1:{port}/map-api/health"
    deadline=time.time()+timeout
    last=None
    while time.time()<deadline:
        try:
            with urllib.request.urlopen(url,timeout=2) as response:
                value=json.load(response)
            if value.get("engine")=="QueueGo/Valhalla":
                return value
            last=RuntimeError("Unexpected health payload")
        except Exception as error:
            last=error
        time.sleep(.5)
    raise RuntimeError(f"Staging health check failed: {last}")

def stop(name):
    if container_exists(name):
        subprocess.run(["docker","rm","--force",name],check=True)
        return True
    return False

def main():
    p=argparse.ArgumentParser()
    sub=p.add_subparsers(dest="action",required=True)
    start=sub.add_parser("start")
    start.add_argument("--store",required=True)
    start.add_argument("--tag",required=True)
    start.add_argument("--name",default="queuego-map-staging")
    start.add_argument("--port",type=int,default=8765)
    start.add_argument("--replace",action="store_true")
    start.add_argument("--dry-run",action="store_true")
    start.add_argument("--output")
    stop_cmd=sub.add_parser("stop");stop_cmd.add_argument("--name",default="queuego-map-staging")
    status=sub.add_parser("status");status.add_argument("--name",default="queuego-map-staging")
    args=p.parse_args()

    if args.action=="stop":
        print(json.dumps({"removed":stop(args.name),"container":args.name}))
        return
    if args.action=="status":
        exists=container_exists(args.name)
        print(json.dumps({"container":args.name,"exists":exists}))
        raise SystemExit(0 if exists else 3)

    store,active,files=ensure_verified_store(args.store)
    cmd=command_for(store,args.tag,args.name,args.port)
    report={
        "container":args.name,
        "image":args.tag,
        "active_package":active,
        "store":str(store),
        "listen":f"127.0.0.1:{args.port}",
        "public_bind":False,
        "tls_terminated_here":False,
        "reverse_proxy_required":True,
        "memory_limit":DEFAULT_MEMORY,
        "cpu_limit":DEFAULT_CPUS,
        "pids_limit":int(DEFAULT_PIDS),
        "read_only_root":True,
        "data_read_only":True,
        "no_new_privileges":True,
        "capabilities_dropped":"ALL",
        "command":cmd,
    }
    if args.dry_run:
        print(json.dumps(report,ensure_ascii=False,indent=2))
        if args.output:Path(args.output).write_text(json.dumps(report,indent=2)+"\n")
        return

    if container_exists(args.name):
        if not args.replace:raise RuntimeError("Container already exists; use --replace explicitly")
        stop(args.name)
    subprocess.run(cmd,check=True)
    try:
        health=wait_health(args.port)
        report["health"]=health
        report["healthy"]=True
        report["same_origin_paths"]=["/map-view/","/map-api/","/map-assets/"]
        if args.output:Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
        print(json.dumps(report,ensure_ascii=False))
    except Exception:
        subprocess.run(["docker","logs",args.name],check=False)
        stop(args.name)
        raise

if __name__=="__main__":
    main()
