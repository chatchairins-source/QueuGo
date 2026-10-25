# Nationwide live acceptance — 25 October 2026

Status: IN PROGRESS. Repository PR #704 publication: 0c3e925236e8ae6634c55a4ea35ecad0c3e4b96e. Tree matches locally integrated/tested source. Hosted QueueGo RC run 40170835613 was still running at verification time.

Read-only QueueTech Production verification:
- Hierarchy: 77 provinces, 928 districts, 7,364 subdistricts, one country.
- Exact-name search: all 77 province queries return their province first.
- Existing service-area RPC accepts short routes at tested real city coordinates in Bangkok, Chiang Mai and Phuket under Thailand Nationwide Launch, with 15 km cap retained.
- Province reference-point matrix: 63 accepted; 14 rejected. This is not proof of missing provinces: administrative reference coordinates can be offshore. Examples from imported source: Phang Nga 9.406,97.901; Phuket 7.924,98.455; Chon Buri 12.611,100.889; Trang 7.033,99.449.
- Rejected reference-point provinces: Chon Buri, Rayong, Chanthaburi, Trat, Nakhon Si Thammarat, Krabi, Phang Nga, Phuket, Surat Thani, Ranong, Chumphon, Songkhla, Satun, Trang.
- Current Places categories: administrative_area 8,370; food 1; market 1. This does not establish comprehensive nationwide villages, roads or POIs.

NEXT: finish hosted RC; assess real on-land/coastal/island addresses against the country polygon and inspect reference coordinate semantics before changing search pin placement or broadening service boundaries. Continue nationwide road/vector runtime and native shared lookup integration. No completed delivery, native build or nationwide navigation acceptance is certified by these SQL reads.

## Resumed coastal audit

- The previously pending hosted RC `40170835613` completed successfully on `0c3e925236e8ae6634c55a4ea35ecad0c3e4b96e`.
- Reproducible query: `node scripts/audit-thailand-boundary.cjs --sql`. This calls only the existing public service-area RPC and creates no data or orders. All 22 locations are approximate locality samples; they are not asserted to be verified delivery addresses.
- Live result: 18 accepted, 4 rejected (Ranong, Ko Tao, Ko Phi Phi Don, Ko Lipe). Offline point containment of the deployed source geometry reproduces all four failures. The deployed polygon has 3,317 vertices in 20 polygon parts.
- Downloaded the exact geoBoundaries candidate via the pinned Git LFS content URL in the audit script; payload length 3,166,313 and SHA-256 `226eeef03694dc708201f142c992a819798841c0302c89e78cac8579b5058cde` both match its source pointer. Country metadata confirms THA/ADM0, represented year 2017 and ODbL-1.0 (OpenStreetMap/Wambacher), rather than a blanket CC-BY assumption. Official metadata: https://www.geoboundaries.org/api/current/gbOpen/THA/ADM0/ .
- Candidate has 70 polygon parts and covers both endpoints in 21 of 22 samples, including all four deployed failures. The approximate Ko Lanta sample is excluded; investigate its exact land/address semantics before labelling the candidate better for every location. No bounding-box or buffered-country workaround was applied.
- Review-only checks are planar point-in-polygon comparisons, not PostGIS topology certification, road connectivity, cross-water route acceptance, current border certification or real delivery completion. Candidate acquisition succeeded; Production boundary replacement remains unperformed until those checks and provenance/attribution handling pass.

- Independent local Shapely/GEOS inspection reports Valid Geometry, 70 polygon parts and 93,940 vertices. A separate Ko Lanta clinic coordinate from a public university-hosted insurer network list (7.598579, 99.036197; https://www.chanthaburi.buu.ac.th/~office_student/documents/insurance/provider-networks.pdf) is inside both old and candidate polygons at both endpoints. This is an additional public reference, not proof of the current clinic status or precise ground truth for the original approximate sample; no clinic record was imported.
- Live negative controls remain enforced: Kuala Lumpur and open-water coordinates return OUTSIDE_SERVICE_AREA; the 22.128 km Bangkok sample returns DELIVERY_DISTANCE_EXCEEDED with the 15 km cap. These read-only calls did not create orders.
