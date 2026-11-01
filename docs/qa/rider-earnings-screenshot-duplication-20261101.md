# Rider Android earnings screenshot evidence — 2026-11-01

## Source and exact installed-APK evidence

- Source branch PR #783 HEAD: `143320eab59708e1d4e5c5f1597ce5de41828570`.
- Hosted authenticated review: https://github.com/chatchairins-source/QueuGo/actions/runs/41281212480
- Real Rider APK screenshot ZIP: https://github.com/chatchairins-source/QueuGo/actions/runs/41281212480/artifacts/13074165935
- Original metadata: `review_complete=true`, `screens=13`, APK SHA256 `fcbb5e0b630116c6592a3c73fb7fdd068e9def37095854b9a327ec2daed13b9c`.

## Actual artifact inspection

Downloaded the original ZIP and compared the SHA256 of every actual PNG byte stream, not filenames:

| Captured screenshot names | Identical PNG SHA256 prefix |
| --- | --- |
| `05-earnings-7-days.png` / `05-earnings-7-days-ledger.png` | `52e7dd4746f726fb` |
| `06-earnings-30-days.png` / `06-earnings-30-days-ledger.png` | `be7dc21bc9032592` |

Thus 13 named screenshots represented **11 distinct pixel images**, not 13 independent views. Inspection of the actual 7-day and 30-day PNGs showed both the earnings summary and the `บัญชีเงินสด` cash ledger already visible in one viewport. This is a **visual evidence counting defect**, not evidence that the Rider ledger is absent.

Merchant artifact from the same run, https://github.com/chatchairins-source/QueuGo/actions/runs/41281212480/artifacts/13073901594, contains 5 named PNGs and 5 distinct hashes.

## Corrective change and acceptance

Draft PR #784 https://github.com/chatchairins-source/QueuGo/pull/784:

1. If summary and ledger screenshot pixels are identical, retain a single summary screenshot with `cash_ledger_in_same_viewport=true` after independently verifying the ledger heading and fields through UIAutomator; remove the redundant PNG.
2. If the ledger is on a genuinely different viewport, retain two separately hashed screenshots.
3. The screenshot index rejects duplicate filenames, duplicate screenshot hashes and corrupt/truncated PNG chunks.
4. Add negative self-tests, including a synthetic PNG fixture used **only for parser testing**, never published as a screenshot of an app.

The fixed capture must be re-run on a newly installed exact-head APK. Do not reuse the 13-count claim from the old artifact. Passing this gate does **not** certify all Rider screens, real food-delivery lifecycle, visual parity, physical GPS or Production readiness.

Customer authenticated screenshots remain blocked by absent CI review credentials. No production database/order/financial changes are part of this QA patch.
