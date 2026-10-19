# Buriram places snapshot
Source: OpenStreetMap contributors / Geofabrik Thailand, ODbL-1.0.
Attribution: © OpenStreetMap contributors — https://www.openstreetmap.org/copyright
Licence: https://opendatacommons.org/licenses/odbl/1.0/
This open-data catalog is distributed under ODbL-1.0. Preserve its attribution and provenance when copying or adapting it.

Snapshot: 2026-10-15T20:21:00Z. The padded operating envelope includes adjacent border areas; it is not an administrative boundary or exhaustive business inventory.
The 3,079 records include 1,284 business/service category records and 1,795 other named places.
No record grants merchant membership, ordering eligibility, verified address/entrance, current opening status, phone ownership or business approval.

Rebuild from the reference-complete PBF whose SHA256 is recorded in the JSON:
1. python3 ops/maps/normalize_osm.py /path/buriram.osm.pbf /new/path/roads.sqlite
2. python3 ops/maps/export_places.py /new/path/roads.sqlite data/maps/buriram-places.json

The PBF and manifest were recovered from successful Map Engine CI run39135860008 / artifact12140330989 at exact source531c1ac9632a836fa753fa77fd13cc3be93544ce.
Extract SHA256: 426e2d638ba73005a681b0ddb30ffa2638ce9fc392b329cef89f04e70fbe442d.
Nodes preserve mapped points; areas use an unverified representative point inside/on the source polygon, not an invented entrance. Distinct OSM identities remain distinct; do not collapse branches merely by shared name.
Phone, website, hours and address exist only where present in OSM tags. No ratings, photos, prices or missing business details are invented. Potential cross-object duplicates require review before merchant linking.
