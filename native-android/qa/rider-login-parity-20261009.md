# Rider login blueprint correction — 2026-10-09

Status: **Visual parity NOT CERTIFIED; Native registration source flow IMPLEMENTED, physical/rendered parity still OPEN.**

Owner identified the fresh Native login screenshot as visibly different from Production. Previous source/build gates never established rendered parity. Fetched596d5e9, preserved history; backup-native-before-rider-login-blueprint-20261009 created locally/GitHub before source edits.

## Actual Production inspection

Opened https://chatchairins-source.github.io/QueuGo/rider/ with a real fresh session. Inspected its rendered screenshot and DOM geometry without reading credentials or entering account/order data. Observed viewport1363×936: actual login card430×462, heading29px/700 with44px line box, description12px/18px line box, labels11px/850 with17px line boxes, input50px/radius15px, primary/secondary52px/radius18px, footer9px/14px line box. Primary is #f04455; border #e6dfe2; text #17171b; muted #77747b. Login action is enabled before filling, validates empty fields locally.

Production media rule max-width800 hides the brand/hero panel, retains card width min(430px, viewport−34px), and puts it24px below the safe top with28px inner padding/radius30px. Previous Native erroneously showed the desktop hero above the mobile form, used Material outlined floating labels/default metrics, wrong colours/proportions, missing signup action and Native footer.

## Safe correction

RiderLoginScreen uses actual Production mobile breakpoint, gradient stops160deg/54%, card/control/text dimensions and exact labels/phone placeholder/Beta footer. Labels are above native BasicTextFields, primary action validates empty fields without sending a request, busy submission remains gated, and existing signIn/session/role/backend logic is unchanged. Scrolling and Android safe area/keyboard handling remain native; no WebView.

**2026-10-10 registration correction:** the temporary external-browser signup path has been retired. The login button now opens the Native `RiderRegistrationScreen` directly. The Native flow contains the four Production steps (ส่วนตัว → รถและพื้นที่ → เอกสาร → ยืนยัน), uses the system document picker, requires the six Rider document slots, uses the shared Native strong-password policy, stores a resumable local draft/checkpoint, creates or resumes the Rider authentication account, writes the pending `rider_profiles` application, and returns pending applicants to the Native registration/pending-account path. `tests/native-rider-registration.cjs` now fails if signup regresses to `Intent`/`ACTION_VIEW`/external-browser navigation or if the four-step/documents/pending-review wiring disappears.

This closes the old **missing Native registration implementation** source gap only. It does **not** certify pixel parity, real document uploads, Production signup writes, Admin approval handoff, device lifecycle, or the complete physical Rider onboarding flow. Those remain observed-device/E2E gates.

The original 2026-10-09 login correction passed its then-current local Gradle/source checks. The 2026-10-10 Native registration correction adds a dedicated source regression and must still pass the current branch/full Native CI before merge. Actual matched Production-mobile ↔ Native registration screenshots remain pending.

## Evidence limits

No matched mobile Web capture yet. Browser policy blocked chrome://inspect viewport control; do not bypass that restriction through alternate browser surfaces/CDP/emulated CSS. Attempted saving the observed desktop screenshot through the documented shared folder failed ENOSPC; no missing/empty file is presented as evidence. Native CI capture must be inspected; it still cannot establish one-to-one without matched mobile Production rendering.

Authenticated Rider Home/Offer/Active/Pickup/Delivery, Customer/Merchant comparisons, real normal/Market/Laundry E2E and physical FCM remain unverified. No APK/RC/Pilot/Play/P0-P1 clearance claim.
