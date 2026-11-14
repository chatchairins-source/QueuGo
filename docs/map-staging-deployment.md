# QueueGo Map staging deployment

This is the next deployment boundary after the Buriram engine candidate. It does **not** provision a cloud VM, create billing, change Production DNS or mutate Supabase.

## What is ready

`ops/maps/stage_container.py` runs the verified active offline package with the candidate QueueGo Map image on an **authorized non-Production Docker host**.

Safety defaults:

- engine binds only `127.0.0.1`
- package mount is read-only
- root filesystem is read-only
- all Linux capabilities are dropped
- no-new-privileges is enabled
- memory / CPU / PID limits are set
- package is verified with the existing offline-package manifest before startup
- health must pass before the command reports success
- no public fallback router is enabled

The staging host must already have Docker and the verified package store installed. Public TLS is intentionally a separate ingress responsibility. The reverse proxy should terminate HTTPS and proxy the whole QueueGo Map origin to the loopback port so `/map-view/`, `/map-api/` and `/map-assets/` remain same-origin.

## Example on an authorized staging VM

Build or load the exact candidate image, then:

```bash
python ops/maps/stage_container.py start \
  --store /srv/queuego-map/packages \
  --tag queuego-map:<verified-sha> \
  --name queuego-map-staging \
  --port 8765 \
  --output /tmp/queuego-map-staging.json
```

Do **not** expose port 8765 publicly. Configure the host's existing TLS reverse proxy to forward the staging hostname to `127.0.0.1:8765`.

For a replacement after a new verified package/image is ready:

```bash
python ops/maps/stage_container.py start \
  --store /srv/queuego-map/packages \
  --tag queuego-map:<new-verified-sha> \
  --name queuego-map-staging \
  --port 8765 \
  --replace
```

Stop:

```bash
python ops/maps/stage_container.py stop --name queuego-map-staging
```

Dry-run the exact Docker command without starting a container:

```bash
python ops/maps/stage_container.py start \
  --store /srv/queuego-map/packages \
  --tag queuego-map:<verified-sha> \
  --dry-run
```

## Remaining owner-provided staging inputs

To perform a real external staging deployment, the repository still needs an explicitly authorized host/runtime target and its access path. Required facts are:

- staging hostname
- authorized VM/container host
- how deployment authentication is supplied
- TLS/reverse-proxy owner
- package store path on that host

Until those are supplied, CI can verify the image/package/runtime contract but must not invent infrastructure or deploy to Production.

## Release boundary

A green staging container is not the navigation release gate. Physical Buriram GPS/road evidence and unresolved upstream turn-restriction verification remain separate gates.
