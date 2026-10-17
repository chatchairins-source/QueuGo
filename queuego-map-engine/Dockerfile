# Candidate runtime only; requires a verified active package mounted read-only.
FROM python:3.12-slim-bookworm
WORKDIR /app
COPY ops/maps/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY ops/maps/offline_package.py ops/maps/fetch_web_assets.py ops/maps/web-assets.json /app/ops/maps/
RUN python /app/ops/maps/fetch_web_assets.py /app/map-assets
COPY queuego-map-engine/core.py queuego-map-engine/server.py queuego-map-engine/start_installed.py /app/queuego-map-engine/
COPY map-view/ /app/public/map-view/
RUN useradd --uid 10001 --create-home queuego
USER 10001
EXPOSE 8765
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/map-api/health',timeout=3)"
ENTRYPOINT ["python","/app/queuego-map-engine/start_installed.py","--store","/data","--web-root","/app/public","--assets-root","/app/map-assets","--host","0.0.0.0","--port","8765"]
