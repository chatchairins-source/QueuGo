# QueueGo — Play App Access, Target Audience & Content Rating Draft

Last reviewed: 2026-10-07

This document is a Play Console preparation sheet. Do not place real passwords, production-user credentials, service-role keys, signing passwords, or personal data in this file.

Official references:
- App review / sign-in details: https://support.google.com/googleplay/android-developer/answer/15748846
- Prepare app for review: https://support.google.com/googleplay/android-developer/answer/9859455
- Content ratings / IARC: https://support.google.com/googleplay/android-developer/answer/9859655
- Target audience: https://support.google.com/googleplay/android-developer/answer/9867159

## 1. App Access / Sign-in details

Google Play requires reviewer access for any functionality restricted by login, membership, location or other authentication. Reviewer credentials must remain valid, reusable and independent of one-time passwords or manual QueueTech intervention.

QueueGo therefore needs **dedicated Play-review accounts**. Do not use an actual customer, shop owner, Rider or administrator account.

### Customer — `com.queuego.customer`

Play Console selection:
- **All or some functionality is restricted**: Yes

Reviewer instruction draft:

> Open QueueGo and sign in using the dedicated Customer review account supplied in Play Console.  
> The account is already active and does not require OTP, manual approval or payment.  
> Main review paths: Home → shop/service → cart/checkout, Orders → tracking, Profile → Privacy / Account deletion, and Customer–Rider chat when a review order is available.  
> QueueGo is pilot-area controlled. If order placement is unavailable because the reviewer is outside the service area, use the preconfigured review scenario/instructions supplied with the review account. Do not require the reviewer to spoof GPS.

Required before submission:
- Dedicated reusable Customer reviewer login
- No OTP
- No expiring password
- No personal user data
- A safe review path that does not require production staff to intervene
- If checkout needs a live order scenario, document exactly how the reviewer reaches it without creating financial liability

### Merchant — `com.queuego.merchant`

Play Console selection:
- **All or some functionality is restricted**: Yes

Reviewer instruction draft:

> Sign in with the dedicated Merchant review account supplied in Play Console.  
> The shop profile must already be approved and active.  
> Main review paths: Dashboard, Orders, Products, shop profile/location, notifications, Privacy / Account deletion and QueueGo Support.  
> The account must not require QueueTech to manually approve it during review.

Required before submission:
- Dedicated active shop reviewer account
- Approved shop/profile already configured
- At least one non-sensitive product or an otherwise reviewable product screen
- No real merchant financial data
- No OTP/manual approval dependency

### Rider — `com.queuego.rider`

Play Console selection:
- **All or some functionality is restricted**: Yes

Reviewer instruction draft:

> Sign in with the dedicated Rider review account supplied in Play Console.  
> The Rider profile must already be approved and able to open the Rider interface without QueueTech intervention.  
> Main review paths: online/offline state, job UI when a review job exists, navigation controls, proof UI, message safety controls, notifications, Profile → Privacy / Account deletion.  
> Do not require a reviewer to submit a real vehicle document or wait for manual approval.

Required before submission:
- Dedicated approved Rider reviewer account
- No OTP/manual approval
- No real Rider identity/vehicle document
- Reviewable non-financial flow for screens that normally depend on an active delivery

## 2. Reviewer-location rule

Do **not** give reviewers a location-dependent password or require location spoofing.

QueueGo's Service Area is a real business constraint. For review, provide either:
1. a reviewer account / review scenario that can access the relevant screens independent of physical location; or
2. clear Play Console instructions explaining which features are location-gated and a reusable route to the rest of the app.

Google requires review access credentials/instructions to remain valid regardless of reviewer location.

## 3. Target Audience decision — CLOSED BETA

Current QueueGo Closed Beta target for all three Android apps:

- **QueueGo Customer (`com.queuego.customer`): Ages 18 and over only.**
- **QueueGo Merchant (`com.queuego.merchant`): Ages 18 and over only.**
- **QueueGo Rider (`com.queuego.rider`): Ages 18 and over only.**
- **Restrict Minor Access: Enable for the Closed Beta/Pilot apps in Play Console.**

Why this is the correct Pilot boundary:
- Customer uses cash transactions, precise delivery location, 1:1 Customer–Rider chat, user-generated images/messages and real-world delivery interaction.
- Merchant is a business/operator role with store operations, GP/accounting and cash responsibility.
- Rider is a work/delivery role with location, vehicle/work responsibility and cash handling.
- The current product has not been designed, consented or moderated as a service for children.

Google Play states that apps targeting children become subject to Families requirements. Selecting only Ages 18 and over keeps the current Pilot out of those child-targeting requirements. For the Pilot, enable Play's **Restrict Minor Access** control so accounts Google identifies as minors cannot discover/download the app.

Do not add any under-18 target age group merely to increase reach. Before a future Production expansion to younger users, perform a new policy/privacy/UGC review, including parental-consent requirements where applicable, age-appropriate content safeguards, ads treatment and Data Safety.

Policy reference:
- https://support.google.com/googleplay/android-developer/answer/9867159

## 4. Content Rating / IARC preparation

Every submitted QueueGo app needs its own IARC questionnaire.

Do not copy a generated rating from one app to another without answering each app's questionnaire accurately.

### Customer — likely questionnaire considerations

Features present:
- User accounts
- Real-world commerce/order transactions
- Location
- 1:1 user communication by chat and user-initiated in-app voice calls
- User-generated text/images
- Reporting/blocking/moderation
- Merchant promotions may be shown

Expected declarations to review carefully:
- **User interaction / communication:** Yes
- **User-generated content:** Yes
- **Location sharing/use:** Yes, for delivery/service functionality
- **Purchases / commerce:** Yes, real-world goods/services; verify exact IARC wording in Console
- **Violence / sexual / gambling / controlled substances:** answer from actual catalog/content, not from this draft
- **Ads:** **Yes** — Customer can display paid merchant Promote placements; use the conservative Play declaration below

### Merchant — likely questionnaire considerations

Features present:
- Business/shop management
- Orders and commerce data
- Customer ↔ Shop user-initiated in-app voice calls on active orders
- Merchant ↔ QueueGo support messages
- Shop/product images
- Promotion-management feature

Review carefully:
- Communication: Yes — Merchant can receive QueueGo voice calls from an active-order Customer and also has support messaging
- UGC: product/shop content is merchant-generated; answer IARC wording accurately
- Purchases/commerce: real-world commerce management
- Ads: **No** for the current Merchant app — it manages a merchant's own Promote requests but does not display third-party ads to the merchant

### Rider — likely questionnaire considerations

Features present:
- Real-world work/delivery
- Location
- Customer ↔ Rider 1:1 chat
- Customer ↔ Rider user-initiated in-app voice calls
- User-generated text/images
- Proof photos
- Report/block controls

Review carefully:
- User interaction / communication: Yes
- User-generated content: Yes
- Location use: Yes
- Real-world commerce/workflow: Yes where questionnaire asks
- Ads: **No** for the current Rider app — no ad-display surface is present

## 5. “Contains ads” decision — RESOLVED FOR CURRENT BUILD

Google Play requires a per-app declaration. Current QueueGo decision:

- **QueueGo Customer (`com.queuego.customer`): Yes — Contains ads.**
- **QueueGo Merchant (`com.queuego.merchant`): No.**
- **QueueGo Rider (`com.queuego.rider`): No.**

Why Customer is declared **Yes**:
- Customer has a `โปรโมชั่นจากร้าน` surface backed by `qg_public_promotions()`.
- Merchant Promote records contain `bid_price`, `max_budget`, `period_days` and `payment_status`.
- Admin approval explicitly keeps Promote unavailable until payment and labels the queue as merchant advertising.
- `qg_public_promotions()` can expose active merchant placements without requiring an app update.
- Production currently has 0 promotion rows, 0 active rows and 0 paid rows, but an empty table today does not remove the server-controlled advertising capability.

Why Merchant and Rider are **No** for the current submitted apps:
- Merchant provides a management/purchase workflow for the merchant's own promotion; it does not render third-party advertisements to the merchant.
- Rider has no promotion/ad display surface in the reviewed build.

Google Play's current review guidance says the Contains ads declaration covers ad SDK ads, display/banner ads and native ads. Because QueueGo's Customer Promote feature is a paid merchant placement surface, **Yes** is the conservative declaration even though no paid campaign is active in production today.

Re-evaluate these declarations if:
- Customer Promote is completely disabled/removed from both client and server exposure before submission; or
- Merchant/Rider later begin displaying third-party or sponsored placements.

Policy reference:
- https://support.google.com/googleplay/android-developer/answer/9859455

## 6. Content safeguards already implemented

For Customer ↔ Rider order chat:
- explicit community rules acceptance before posting
- prohibited-content rules
- report user
- report individual message
- block/unblock counterpart
- server-side send denial while either side blocks
- moderation queue
- immutable report snapshot
- account-deletion privacy cleanup
- chat retention window

Evidence:
- `docs/community-guidelines.html`
- `docs/privacy.html`
- `queuego-ugc.js`
- `tests/ugc-chat-safety.cjs`

## 7. Before Play submission

For each of the three apps:
- [ ] Dedicated reusable reviewer account created
- [ ] Account works without OTP/manual approval
- [ ] English Play Console access instructions entered
- [ ] Privacy policy URL entered
- [ ] Data Safety completed from final production behavior
- [ ] Target Audience entered in Play Console: **18+ only** for Customer, Merchant and Rider; enable **Restrict Minor Access** for Closed Beta
- [ ] IARC questionnaire completed
- [ ] Contains Ads entered in Play Console: Customer **Yes**, Merchant **No**, Rider **No** (revalidate if submitted behavior changes)
- [ ] UGC/communication declarations match actual chat and in-app voice features
- [ ] Microphone permission declaration matches user-initiated QueueGo voice calls only; no call recording/background capture
- [ ] Store listing screenshots match current production UI
- [ ] Account deletion path verified
- [ ] App access does not depend on QueueTech replying during review

## Hard boundary

Creating special reviewer accounts or review orders must not weaken production authorization, bypass RLS, expose service credentials, or introduce a hidden production backdoor. Any review-only data must use ordinary QueueGo roles and ordinary authorization rules.
