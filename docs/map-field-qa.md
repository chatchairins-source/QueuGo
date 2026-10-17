# QueueGo Map physical field QA

Physical road/GPS evidence is a separate release gate from graph/CI success. The web map contains an opt-in recorder that is **off by default**.

## Start a field session

On an authorized staging origin, open:

```
/map-view/?field-qa=1
```

The Field QA panel appears only with that query parameter. It records device GPS locally in the browser and does not upload raw GPS automatically.

Choose the scenario before starting:

- ซอย/ถนนในหมู่บ้าน
- ถนนคู่ขนาน/ถนนหน้า
- จุดกลับรถ
- ถนนคู่ขนาน GPS ใกล้กัน
- สัญญาณ GPS หาย
- สลับพื้นหลัง → กลับเข้าแอป
- ทดสอบทั่วไป

For navigation validation, calculate a route and start QueueGo navigation **before** starting the Field QA recorder. The session captures the active route ID/profile, accepted GPS fixes, matching method, snapped position, off-route distance, reroute observations, arrival observations, GPS gaps and visibility changes.

Do not operate the phone while driving. Start the recorder before moving or have a passenger/tester operate the device.

## Export and validate

Use the panel's export action. It produces versioned JSON with schema:

`queuego-map-field-evidence/v1`

Validate locally:

```bash
python ops/maps/validate_field_evidence.py queuego-field-....json --output evidence/map-engine/field-session.json
```

The validator checks minimum duration/fixes, accepted GPS ratio, median accuracy, Buriram package bounds, physical motion, map-matching evidence for relevant scenarios, real GPS gaps for signal-loss sessions and hidden→visible events for background/foreground sessions.

A validator pass means **candidate evidence suitable for human review only**. It never marks a road, sign, U-turn or turn restriction as certified and never closes the release gate automatically.

## Evidence needed before sole-navigation release

At minimum collect separate sessions for:

1. named village lane / small residential road
2. frontage/service-road behavior
3. real U-turn
4. parallel roads where GPS could snap to the wrong carriageway
5. deliberate GPS-loss/recovery
6. background → foreground navigation restoration
7. an active route with real reroute
8. an active route reaching destination

Malformed OSM relations 2148441 and 17320674 require independent sign/road verification. Do not infer missing restriction members from GPS alone.
