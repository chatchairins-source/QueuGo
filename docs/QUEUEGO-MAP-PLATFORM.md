# QueueGo Map Platform

## Goal

QueueGo Map is not a skin over Google Maps. It is a QueueGo-owned map UX and calculation layer built on permitted/open geospatial sources and routing engines. Upstream licenses and attribution still apply to source data.

The browser prototype is intentionally map-first. Heavy tools remain hidden until the user opens the QueueGo Map toolbox.

## Consumer map feature families researched

Large map products commonly expose:

- place/address search and nearby discovery
- current location and dropped pins
- forward and reverse geocoding
- latitude/longitude inspection
- driving directions and route alternatives
- intermediate waypoints / multi-stop routes
- optimized waypoint ordering
- travel-time and distance comparison
- route matrix calculations for dispatch
- traffic overlays and incidents
- road closures / construction / access restrictions
- turn-by-turn navigation and rerouting
- measure distance between multiple points
- map layers such as transit, cycling, terrain, satellite, street imagery, air quality and 3D views
- saved places, offline areas and sharing

Google Maps Platform also packages developer-facing calculations such as Routes, Route Matrix, Route Optimization, Roads/Snap-to-Road, Geocoding, Places/Nearby, Elevation, Geolocation, Time Zone, Weather, Air Quality and dataset/tiles services.

These are feature references only. QueueGo does not copy Google source code, proprietary map content, imagery, APIs or UI assets.

## QueueGo Map Engine — implemented web prototype

Current `/map-view/` exposes `window.QueueGoMapEngine` with:

- `geocode(query)`
- `reverseGeocode(lat, lon)`
- `nearestRoad(lat, lon)`
- `route(points)`
- `routeMatrix(points)`
- `optimizeStops(points)`
- `matchTrace(points)`
- `nearby(category, lat, lon, radius)`
- `distanceMeters(pointA, pointB)`
- `bearingDegrees(pointA, pointB)`

UI tools:

- QueueGo Map branded search
- pin / address capture
- coordinate + nearest-road inspector
- copy latitude/longitude
- multi-point straight-line distance measurement
- up to 12 interactive waypoints in the browser prototype
- route in user-selected stop order
- automatic stop reordering using the routing engine trip service
- all-to-all travel-time and distance matrix
- nearby POI categories
- route alternatives ranked by enriched ETA
- traffic-signal count per route
- open-data construction/access restriction checks
- local historical observed-speed learning after a minimum sample threshold
- live GPS navigation status
- repeated off-route detection, accuracy gate, reroute cooldown and automatic reroute

## Open prototype dependencies

The browser prototype currently uses:

- OpenStreetMap raster tiles for display
- Nominatim for text/reverse geocoding
- Overpass for POI, traffic-signal and road-restriction discovery
- public OSRM endpoints for route, table, trip, nearest and match calculations
- Department of Provincial Administration open village data where available

The public endpoints are suitable for research and functional validation, not the final commercial traffic volume.

## QueueGo commercial API target

The production platform should graduate the browser engine into QueueGo-owned services:

- `POST /v1/geocode`
- `POST /v1/reverse-geocode`
- `POST /v1/nearest-road`
- `POST /v1/snap-to-road`
- `POST /v1/route`
- `POST /v1/route-matrix`
- `POST /v1/optimize-stops`
- `POST /v1/optimize-fleet`
- `POST /v1/nearby`
- `POST /v1/isochrone`
- `POST /v1/elevation`
- `POST /v1/timezone`
- `POST /v1/traffic`

Each response should include source/provenance version, graph version, calculation timestamp, confidence where relevant and licensing/attribution metadata.

## Next production layers

1. Self-hosted QueueGo road graph from licensed/open data instead of public demo routing endpoints.
2. Separate profiles for car, motorcycle, walking and cycling.
3. Offline map packages plus offline routing graph.
4. QueueGo POI/search index with Thai village/soi/address normalization.
5. Anonymous aggregated speed observations for historical traffic modeling.
6. Authoritative incident/closure feeds where available.
7. Fleet optimization for Rider stack, Food, Express and Ride.
8. Turn-by-turn maneuver generation, voice guidance, map matching and navigation camera.
9. Elevation, isochrone/travel-time areas and terrain.
10. Licensed/open satellite/orthophoto imagery where available. Street-level imagery must come from a source whose license permits QueueGo use.

## Safety / integrity

- Never fabricate live traffic.
- Never show a signal countdown without an authoritative controller/SPaT feed.
- Do not route through a road known to be inaccessible merely because it is shorter.
- Poor GPS confidence must not trigger immediate rerouting.
- Source conflicts remain traceable; no silent conflation.
