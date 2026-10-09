# Rider login blueprint correction — 2026-10-09

Status: **Visual parity NOT CERTIFIED; functional registration parity FAIL.**

Owner identified the fresh Native login screenshot as visibly different from Production. Previous source/build gates never established rendered parity. Fetched596d5e9, preserved history; backup-native-before-rider-login-blueprint-20261009 created locally/GitHub before source edits.

## Actual Production inspection

Opened https://chatchairins-source.github.io/QueuGo/rider/ with a real fresh session. Inspected its rendered screenshot and DOM geometry without reading credentials or entering account/order data. Observed viewport1363×936: actual login card430×462, heading29px/700 with44px line box, description12px/18px line box, labels11px/850 with17px line boxes, input50px/radius15px, primary/secondary52px/radius18px, footer9px/14px line box. Primary is #f04455; border #e6dfe2; text #17171b; muted #77747b. Login action is enabled before filling, validates empty fields locally.

Production media rule max-width800 hides the brand/hero panel, retains card width min(430px, viewport−34px), and puts it24px below the safe top with28px inner padding/radius30px. Previous Native erroneously showed the desktop hero above the mobile form, used Material outlined floating labels/default metrics, wrong colours/proportions, missing signup action and Native footer.

## Safe correction

RiderLoginScreen uses actual Production mobile breakpoint, gradient stops160deg/54%, card/control/text dimensions and exact labels/phone placeholder/Beta footer. Labels are above native BasicTextFields, primary action validates empty fields without sending a request, busy submission remains gated, and existing signIn/session/role/backend logic is unchanged. Scrolling and Android safe area/keyboard handling remain native; no WebView.

**Registration remains a real functional gap:** signup now opens the existing Production Rider site in an external browser; the user must choose signup there. This is a temporary existing-service access path, not a native registration implementation or one-to-one flow. Do not certify this page's full functional parity until the native four-step registration/documents/confirmation/pending flow exists and is tested. No new signup backend/data submission is introduced in this UI batch.

Local Gradle shared/customer/rider JVM tests and Customer/Merchant/Rider build PASS (144tasks; Rider compile/test rebuilt). Source191/launcher61/category12/whitespace PASS. Full regression/CI and actual corrected Native capture pending their actual results.

## Evidence limits

No matched mobile Web capture yet. Browser policy blocked chrome://inspect viewport control; do not bypass that restriction through alternate browser surfaces/CDP/emulated CSS. Attempted saving the observed desktop screenshot through the documented shared folder failed ENOSPC; no missing/empty file is presented as evidence. Native CI capture must be inspected; it still cannot establish one-to-one without matched mobile Production rendering.

Authenticated Rider Home/Offer/Active/Pickup/Delivery, Customer/Merchant comparisons, real normal/Market/Laundry E2E and physical FCM remain unverified. No APK/RC/Pilot/Play/P0-P1 clearance claim.
