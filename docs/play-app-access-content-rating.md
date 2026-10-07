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

## 3. Target Audience draft

### Merchant

Recommended Play target:
- **18 and over**

Reason:
- Business account
- Store operations
- Cash/GP/accounting functions
- Legal/business responsibilities

### Rider

Recommended Play target:
- **18 and over**

Reason:
- Work/delivery role
- Cash handling
- Vehicle/work responsibility
- Location-dependent work flow

### Customer

Business decision still required before Play submission.

Recommended default for Pilot:
- **18 and over**, unless QueueTech intentionally designs the service for minors.

Why the conservative default fits the current Pilot:
- Cash transactions and orders
- Precise delivery location
- 1:1 Customer–Rider chat
- User-generated images/messages
- Real-world delivery interaction

If QueueTech intentionally targets ages under 18, reassess:
- Target Audience selection
- Families policy implications
- Age-appropriate UGC protections
- Data Safety / personal-data handling
- Store listing imagery and language

Do not select child age groups merely to increase reach. Selecting children triggers additional Google Play Families requirements.

## 4. Content Rating / IARC preparation

Every submitted QueueGo app needs its own IARC questionnaire.

Do not copy a generated rating from one app to another without answering each app's questionnaire accurately.

### Customer — likely questionnaire considerations

Features present:
- User accounts
- Real-world commerce/order transactions
- Location
- 1:1 user communication
- User-generated text/images
- Reporting/blocking/moderation
- Merchant promotions may be shown

Expected declarations to review carefully:
- **User interaction / communication:** Yes
- **User-generated content:** Yes
- **Location sharing/use:** Yes, for delivery/service functionality
- **Purchases / commerce:** Yes, real-world goods/services; verify exact IARC wording in Console
- **Violence / sexual / gambling / controlled substances:** answer from actual catalog/content, not from this draft
- **Ads:** requires business decision below

### Merchant — likely questionnaire considerations

Features present:
- Business/shop management
- Orders and commerce data
- Merchant ↔ QueueGo support messages
- Shop/product images
- Promotion-management feature

Review carefully:
- Communication: Yes where support messaging is treated as communication
- UGC: product/shop content is merchant-generated; answer IARC wording accurately
- Purchases/commerce: real-world commerce management
- Ads: see business decision

### Rider — likely questionnaire considerations

Features present:
- Real-world work/delivery
- Location
- Customer ↔ Rider 1:1 chat
- User-generated text/images
- Proof photos
- Report/block controls

Review carefully:
- User interaction / communication: Yes
- User-generated content: Yes
- Location use: Yes
- Real-world commerce/workflow: Yes where questionnaire asks

## 5. “Contains ads” decision — DO NOT GUESS

QueueGo has a Merchant Promote / promotion feature.

Before answering the Play Console “Contains ads” declaration, determine whether the Customer app displays merchant placements that are:
- paid,
- sponsored,
- promoted for commercial consideration, or
- otherwise advertising rather than an organic store/product listing.

If paid/promoted merchant placements appear to users, declare ads as required by the exact Play Console wording and ensure the content rating is compatible.

If Promote is disabled/not displayed in the submitted build, document that build-state evidence before choosing “No”.

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
- [ ] Target audience declared
- [ ] IARC questionnaire completed
- [ ] Contains Ads declaration confirmed against final Promote behavior
- [ ] UGC declarations match actual chat/content features
- [ ] Store listing screenshots match current production UI
- [ ] Account deletion path verified
- [ ] App access does not depend on QueueTech replying during review

## Hard boundary

Creating special reviewer accounts or review orders must not weaken production authorization, bypass RLS, expose service credentials, or introduce a hidden production backdoor. Any review-only data must use ordinary QueueGo roles and ordinary authorization rules.
