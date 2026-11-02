"""Read-only UIAutomator acceptance for Rider native navigation diagnostics.

This validates real visible Android nodes before a screenshot is counted.
It is not a substitute for visual image inspection or physical GPS acceptance.
"""
import re

REQUIRED = frozenset(("อินเทอร์เน็ต", "สิทธิ์ตำแหน่ง", "บริการตำแหน่ง / GPS", "การแจ้งเตือน"))
OPTIONAL = "Google Maps (ทางเลือก)"
SUMMARY = re.compile(r"ผ่าน [0-4]/4 รายการหลัก\Z")


def visible_texts(nodes):
    """Ignore stale/non-visible hierarchy entries; never persist raw UI text."""
    result = set()
    for node in nodes:
        bounds = node.get("bounds", "")
        coords = [int(v) for v in re.findall(r"\d+", bounds)]
        if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
            continue
        text = node.get("text", "").strip()
        if text:
            result.add(text)
    return result


def diagnostics_visible(nodes):
    texts = visible_texts(nodes)
    return (
        REQUIRED.issubset(texts)
        and OPTIONAL in texts
        and "รายการแนะนำ" in texts
        and any(SUMMARY.fullmatch(text) for text in texts)
    )
