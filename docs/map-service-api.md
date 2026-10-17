# QueueGo Map service API v1

All roles use the same service/core and installed dataset: Customer, Merchant, Rider, Ride, Express and Admin. Web facade remains `window.QueueGoMapEngine`; transport uses same-origin `/map-api/`. No role gets a second routing implementation. Android should consume these JSON contracts or an eventual binding to the same Valhalla core and package format; JNI/offline runtime is not implemented in this web candidate.

POST JSON for every action. Coordinates are objects `{lat,lon}`; geometry is GeoJSON `[lon,lat]`. Distances are metres, duration/ETA seconds, speed m/s, heading degrees clockwise from north, fix timestamps Unix **seconds** (browser converts milliseconds). Non-finite/out-of-region coordinates fail. Routes use 2–20 points, HMM traces 1–64 points. API errors are explicit 400/503; no public OSRM fallback or fabricated straight route.

| Path under /map-api | Request | Main response |
|---|---|---|
| geocode | `{q:"บุรีรัมย์"}` | Local OSM named-node POIs, Nominatim-compatible name/lat/lon fields |
| reverse-geocode | `{lat,lon}` | Nearest named feature/road with explicit precision; NOT a verified postal address |
| nearest | `{lat,lon,profile}` | `waypoints` with snapped location, OSM way ID and distance |
| route | `{points,profile,preference}` | `routes`, geometry/legs/Thai maneuvers, route_id, cost_components, traversed signalCount |
| route-alternatives | same | Up to 3 distinct candidates, 2-stop alternatives supported |
| matrix | `{points,profile}` | Square durations/distances, unreachable cells null |
| optimize-stops | `{points,profile}` | `order` (input indices), reordered points, trip; first/last fixed |
| match | `{points,profile,route_id?,context?}` | HMM snapped position/matchings; route progress if a cached route_id is supplied |
| nearby | `{lat,lon,category,radius}` | Named local OSM POIs; food/fuel/hospital/police/atm/school/toilet/all, radius up to 20 km |
| traffic-signals | `{bounds?:[west,south,east,north]}` | Canonical OSM signal nodes; no live signal phases/countdown |
| road-restrictions | same | Known access/construction/closure-tagged segments with provenance; not a live closure feed |
| eta | `{route_id,lat,lon,accuracy,timestamp,speed?,heading?,context?}` | Route progress, remaining distance/time, next maneuver, arrival/reroute and updated context |
| health | `{}` | Engine/data versions, bounds/capabilities and optional installed vector URL |

Profiles currently map motorcycle/rider_delivery/express to native motorcycle; car/ride_passenger to native auto. These are routing aliases, not changes to booking/order state. Passenger vehicle semantics require review before wiring a real Ride booking. Do not infer vehicle permission from a role name. All requests enforce OSM access/one-way/turn rules through the native costing; known hard no-access/construction candidates are rejected again on traversed way IDs. Conditional-tag candidates fail closed pending a supported time-aware dataset.

Preferences: fastest uses estimated duration; shortest requests native shortest with hierarchy pruning disabled; fewer_signals ranks computed alternatives by traversed signals; reliable avoids unpaved/tracks and penalizes known rough surface. Ranking is only among returned candidates. There is no global fewest-signal proof, certified safe-route guarantee, live traffic or historical QueueGo speed feed. Base engine time already includes native junction/turn costing; explicit per-signal planning delay is 20 seconds and is not observed/red-light timing. Matrix currently returns native engine time, not the additional candidate signal estimate.

Client navigation passes accurate fixes, a rolling trace, route_id and the returned opaque context. Accuracy >50 m, negative accuracy, stale timestamps and implausible jumps are rejected. Off-route requires 3 repeated distance-evidence fixes plus a 20-second cooldown; signal-loss gaps reset evidence. Arrival requires 3 accurate near-destination fixes. GPS rejection never requests rerouting from one bad fix. Heading is used for route projection while moving at >=2 m/s. Matched position is for display; raw accurate distance evidence is retained for off-route evaluation so snapping cannot hide a true departure.

Route IDs are a bounded in-memory cache (128, sliding 1-hour expiry), not durable navigation state. Save stops, selection and active intent; on restoration recompute from the installed graph. Never trust an old route ID after restart/package activation. Browser restores locally saved stops up to 24 hours old, redraws overlays and restarts navigation if it was active. Job/order state is not part of this map service.

The service binds loopback by default. Optional explicit CORS origins are supported. Before public hosting, configure TLS, request/rate/resource limits and service lifecycle behind a controlled ingress; do not expose the development file server as a public production deployment. No Supabase/database production changes are required by this candidate.

Matching now sends increasing fix times and median accepted accuracy into native HMM candidate search, ignores stationary heading noise and resets the trace after a >15-second gap. Invalid latest fixes are rejected instead of displaying a stale earlier snap. If the graph cannot match an accurate fix on an active route, the result explicitly reports matching unavailable and provides raw `display_position`; it does not label raw GPS as snapped. Raw repeated off-route evidence and cooldown remain active so matching failure cannot permanently suppress rerouting.

Malformed source turn relations are exposed in `data.unresolved_turn_relations`. Relations whose current OSM member directions cannot legally approach/depart the declared via node are exposed separately in `data.source_direction_conflicts`. All member ways of either class are quarantined from usable routes until independently verified. Matrix native times are additionally audited per reachable pair; rejected pairs become null rather than an ETA that might imply permission. This conservative policy can make some regional requests unavailable and adds work to matrix requests. It does not reverse roles, invent missing turn directions, or override current one-way tags. Raw native Valhalla calls are research/benchmark surfaces, not a permitted bypass of the shared QueueGo service policy.
