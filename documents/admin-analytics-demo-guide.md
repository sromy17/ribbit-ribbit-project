# Admin and analytics dashboard demo guide

This is a presenter script for the implemented screens, with a repeatable synthetic dataset, expected results, explanations and fallback paths. Allow **15–20 minutes** for the main demo, or use the five-minute version at the end. All amounts and people in the demo fixture are fictional. The existing seed usernames are used for convenience; they are not the persona names.

## 1. Start a fresh demo

From the repository root:

```sh
# First run only, if dependencies are missing:
npm ci --prefix frontend
npm ci --prefix shared/auth-stub

# Docker must be running; Java 25 and Maven must be installed.
bash scripts/verify-merged-connection.sh --demo
```

Wait for both PASS messages and the UI URL. Open **http://127.0.0.1:14300**. The launcher builds Spring, starts customer authentication and Angular, loads fresh source and OLAP databases, runs ETL, and checks the expected demo totals through the real frontend proxy. It does not alter your usual database or approve any disclosure policy. Demo ports are 14300 (UI), 18182 (Spring), 14100 (customer authentication), plus a random PostgreSQL port. Stop it with Ctrl-C. Restart it to reset resolved cases and issued reports.

| Purpose | Username | Password | Initial sign-in choice |
| --- | --- | --- | --- |
| Demonstrate the admin workspace and supervisor actions | `john_trader` | `mission123` | Staff sign-in |
| Demonstrate Priya's analytics workspace | `jane_investor` | `mission123` | Staff sign-in |
| Optional customer/account comparison | `bob_daytrader` | `mission123` | User sign-in |

These passwords, account grants and the launcher's signing secret are for the disposable local demo only. Ordinary staff accounts must be provisioned separately. The fixture grants `john_trader` platform administrator permissions; David's normal Operations role is read-only for investigations and disputes, so do not imply that every Operations user can resolve cases.

Select **September 2026** and **UTC** for analytics. In Admin Orders, use **2026-09-01 through 2026-09-30**. The default current month may be empty. Leave instrument, side and segment filters blank initially. Keep this guide outside the projected application if you want to read the script verbatim.

### Before the audience arrives

1. Run the launcher and confirm its checks pass.
2. Sign in once with each staff account and verify the appropriate workspace opens.
3. Find the open case titled **“Demo: explain the execution fee”**. Do not resolve it during rehearsal unless you intend to restart the fixture.
4. Check order **800101**, user **3**, and account **103**.
5. In analytics, select `2026-09`, `UTC`, and refresh. Confirm six fills and three active clients.
6. Keep a note where you can copy the report ID after issuing a report.
7. Choose the theme and zoom level before presenting. Charts have equivalent tables; use those if the projector makes the bars hard to read.

## 2. The story and opening script

**Say:**

> “I’ll follow one customer question from investigation to resolution, then show how the same execution records appear in monthly reporting. Operations needs an explanation of an individual trade. Commercial analysis needs reproducible totals across many trades. These workspaces answer those different questions from the same recorded evidence.”

Show the sign-in choice screen.

> “Users sign in to their accounts and trading tools. Staff sign in separately for internal work. The server decides which staff workspace and actions an account can access; choosing this button does not grant a role.”

Choose **Staff sign-in**, enter `john_trader` / `mission123`, and sign in.

## 3. Admin: investigate an order before making a decision

### A. Order search — about two minutes

**Do:** Open **Orders**. Set the UTC range to September 1–30, 2026. Click **Search orders**. Point to the order ID, account, instrument ID, side, quantity, status and recorded creation time. Open **800101**, or enter it under **Have an order ID?** and click **Investigate**.

**Say:**

> “The search helps staff find an order by period, symbol and status. Stable IDs let us open the exact order even if a display symbol changes. The direct ID lookup opens operational evidence independently of the reporting snapshot.”

**Why it exists:** A delayed analytics refresh should not prevent staff from investigating an order. Search results and live investigation have different freshness labels. Point to those labels rather than calling all data real-time.

**Also mention:** Pagination bounds the history returned. A search with no matching rows is an empty result, not proof that the customer's order never existed. Clear the filters or use its ID.

### B. Timeline, fills and financial effects — about three minutes

**Do:** In **Trade investigation**, show lifecycle events, individual fills, cash movements and holding movements for **800101**.

**Say:**

> “This order requested ten shares. It filled as four shares at 100 and six shares at 102. That is two fills but one executed order. Gross execution value is 1,012 GBP. The two one-pound fees bring the recorded cash debit to 1,014 GBP.”

Expected evidence:

| Evidence | Expected record |
| --- | --- |
| Submission / acceptance | Recorded September 10, around 10:00 UTC |
| Fill `810101` | 4 SHARE × 100 GBP; fee 1 GBP |
| Fill `810102` | 6 SHARE × 102 GBP; fee 1 GBP |
| Cash effects | −401 GBP and −613 GBP |
| Holding movements | +4 and +6 shares; running quantities also include the earlier demo order |
| Quote source | `SYNTHETIC_DEMO`, explicitly fictional evidence |

> “The timeline records what happened and when. We do not reconstruct acceptance or rejection by looking only at today's final order status. Fills and financial movements are shown separately so staff can explain the amounts.”

**Do not say:** That the source is a live market feed, that the example executed during the presentation, or that holdings after a fill are the customer's entire present portfolio. This is recorded synthetic evidence. Current holdings are a separate snapshot.

**Optional rejection example:** Investigate **800105**. It has a submission and an explicit validation rejection for insufficient funds, but no fills, cash debit or holding increase. Explain that a rejected request contributes to submission-cohort reporting without contributing executed quantity.

**Optional month-boundary example:** Investigate **800107**. It was submitted and accepted in August; one fill occurred August 31 at 23:59 UTC and another September 1 at 00:01 UTC. September contains the second fill, not a new September submission.

### C. Customer lookup and account history — about two minutes

**Do:** Open **User lookup**, enter **3**, and click **Look up user**. Select account **103**. Switch **View** among Order history, Trade history, Current holdings and Cash movements. Then select account **106**.

**Say:**

> “A user can have several accounts. Here staff can inspect each account's recorded history, current holdings and cash without asking engineering to run a query. The snapshot time tells us when current balances were read; it does not claim to be the balance at the time of a historical trade.”

Expected demo details:

- User 3 is `bob_daytrader`, with accounts 103 and 106.
- Account 103 has **14 shares of DEMO-A**, including both months' fills. Its demo cash balance is **98,181.20 GBP**.
- Account 106 has **2 shares of DEMO-B** and **899.50 GBP** cash.
- Historical seed records can appear alongside the synthetic demonstration records. Use the 800xxx order IDs for this walkthrough.

**Why it exists:** User identity and account identity are different. Staff investigates the account affected, while analytics counts one active client across both accounts. Operations and Compliance read access does not imply permission to edit balances or banking details.

### D. Dispute ownership and resolution — about four minutes

**Do:** Open **Disputes**. Filter to **OPEN** if necessary. Open **“Demo: explain the execution fee.”**

**Say:**

> “This is a trade-specific question about the two execution fees. The case links to its customer, account and order, so the investigator can move from the question to the evidence.”

Follow these actions in order:

| Action | What to show / enter | What to explain |
| --- | --- | --- |
| Claim case | Click **Claim case** | `OPEN → CLAIMED`; the case gains an owner. Claiming reserves responsibility. |
| Accept investigation | Click **Accept investigation** | `CLAIMED → IN_PROGRESS`; the owner is now working it. |
| Inspect evidence | **Investigate trade**, then return to Disputes and reopen the case if needed | The decision should be grounded in the actual fills and movements. |
| Add note | `Reviewed fills 810101 and 810102: fees are GBP 1 each. Total execution fees GBP 2; cash debits GBP 401 and GBP 613 reconcile to GBP 1,014.` | Notes preserve the investigation; internal notes are not customer-facing messages. |
| Resolve | Outcome **Not upheld**. Reason: `Synthetic demo review: the recorded GBP 2 fee equals the sum of the two fill fees. No unexplained debit was found.` | `IN_PROGRESS → RESOLVED`; a reason and outcome record the decision. |
| Inspect history | Show **Case history** and **Resolution recorded** | Actor, time and case versions make the sequence traceable. Reload retains the resolution. |

**Say:**

> “Resolving the case records a decision. It does not automatically refund money, reverse a trade or send a customer notification. Those are separate capabilities and must not be implied by this button.”

**Other implemented actions to explain without consuming the main case:**

- **Release to unassigned pool:** Provide a reason. The case returns to the pool for another eligible admin to claim.
- **Reassign:** A supervisor/admin supplies an eligible admin user ID and a reason. The demo fixture has one admin, so use another provisioned admin only if you have prepared one.
- **Conflicting edits:** Case versions reject stale updates; reload and review another investigator's changes before acting again. Do not promise that a second tab silently overwrites the first.
- **Staff access removed:** Revocation invalidates sessions and returns assigned unresolved cases to the unassigned pool. This is a backend capability, not a staff-management screen in this dashboard.
- **Closed cases:** No reopen action. A customer may submit a new ticket, including a further question about the same transaction. Multiple disputes are allowed; duplicate/related allegations are handled by admin judgment.
- **Eligibility:** Trade and cash movement disputes are supported, including a fee-related flag on a trade. There is no submission deadline. Generic platform support questions are a different ticket type; do not present this dispute queue as a complete generic support inbox.

### E. Admin reporting and freshness — about one minute

**Do:** Open **Reporting**. Point to last publication/freshness information and the activity table.

**Say:**

> “This gives administrators a view of reporting health and delayed historical activity. It is separate from the live investigation. Priya has the fuller analytics workspace for period analysis and issued monthly reports.”

**Important:** The admin compatibility activity endpoint does not use every definition in the newer analytics metric contract. Do not compare its legacy totals to Priya's exact fill-based totals and claim they are interchangeable. **Refresh status** refreshes the displayed status; it is not an ETL-run button. The launcher already published the demo dataset through the protected ETL API.

## 4. Analytics: turn recorded evidence into a monthly report

Sign out. Choose **Staff sign-in**, then use `jane_investor` / `mission123`. This account has reporting permissions and opens Priya's workspace.

### A. Scope and provenance — about two minutes

**Do:** Set **Month** to `2026-09`; keep **Reporting timezone** `UTC`. Leave other filters blank. Click **Refresh analysis**. Open **Coverage and warnings**.

**Say:**

> “Every result has a period, reporting timezone, dataset ID, source cutoff and publication time. These tell us what evidence the report includes and when it was made available. The metric version tells us which definitions were used.”

> “This fixture also retains older seed records with missing precise timestamps and metadata. The incomplete label is deliberate: the system shows known results and tells us where source evidence is missing. It does not invent timestamps or turn unavailable data into zero.”

The full dataset can be **INCOMPLETE**, even though the new demonstration rows have known values. **PROPOSED** means stakeholder metric approval is still pending. **STALE** appears when the dataset exceeds the configured 15-minute threshold; that threshold is implemented but not an approved production service target.

**Why it exists:** All panels and pagination requests are pinned to the published dataset. If publication changes while requests are in flight, refresh the analysis; do not combine panels from different versions. Analytics queries read OLAP and do not fall back to expensive trading-database queries if reporting fails.

### B. Overview and independent arithmetic — about three minutes

Expected September UTC results for the synthetic rows:

| Measure | Expected value | Explanation |
| --- | --- | --- |
| Submitted orders | **6** | Orders 800101–800106 were submitted in September. |
| Accepted orders | **5** | Four September executed orders plus accepted/pending 800106. |
| Fill count | **6** | Two fills for 800101, one each for 800102–800104, and September's fill of 800107. |
| Executed orders | **5** | Distinct orders with a September fill, including August-submitted 800107. |
| Active clients | **3** | Users 3, 4, 5. User 3 is counted once across accounts 103 and 106. |
| Rejection rate | **16.67%** | One rejected order / six orders in the September submission cohort. |
| Pending | **1** | Accepted order 800106 has no fill yet. |
| Gross turnover | **GBP 1,516; USD 61,100** | Native-currency execution value, before fees. |
| Execution fees | **GBP 2.90; USD 6.00** | Sum actual fill fees in their recorded currency. |
| Net cash movements | **GBP −1,518.90; USD −61,106.00** | Recorded cash effects already include the fees. |

**Say:**

> “The two fills on one order do not create two executed orders. A client trading through two accounts does not become two active clients. An August order can contribute a September fill. Each metric has its own grain and event time.”

> “GBP and USD stay separate. Shares, EUR units and Bitcoin quantities also stay separate. We do not add them into one volume number, and we do not subtract fees from cash again.”

**Arithmetic cue:** `4×100 + 6×102 = 1,012`; add September's `2×202 = 404` and Beta's `2×50 = 100` for **GBP 1,516**. USD is `1,000×1.10 + 1×60,000 = 61,100`.

### C. Activity — about one minute

**Do:** Open **Activity**. Show the bars and the equivalent table.

Expected UTC dates: September **1, 10, 11, 12, 14**. September 10 has **two fills**; the other shown dates have one each. Dates with no recorded fills are omitted.

**Say:**

> “This answers when recorded executions happened. The table supplies exact counts for the chart. Daily active-client and executed-order distinct counts are calculated per day; adding them does not necessarily produce monthly unique counts.”

**Optional timezone example:** With `America/New_York`, the September 1 00:01 UTC fill falls on August 31 locally. September then has five fills, four executed orders and GBP gross turnover **1,112**, instead of 1,516. Submission counts remain six. Return to UTC before issuing the report. This is a calendar-boundary demonstration, not a data loss.

### D. Instruments and heatmap — about two minutes

**Do:** Open **Instruments**. Show stable IDs and recorded units. Then open **Heatmap**.

| Instrument | September fills | Quantity |
| --- | --- | --- |
| DEMO-A #8001 | **3** | **12 SHARE** |
| DEMO-EURUSD #8002 | **1** | **1,000 EUR** |
| DEMO-BTC #8003 | **1** | **1 BTC** |
| DEMO-B #8004 | **1** | **2 SHARE** |

Ranking uses fill count, with stable instrument ID to break ties. Use **Instrument ID 8001** and refresh to isolate Alpha, then clear the filter and refresh.

**Say:**

> “The ranking and heatmap show our clients' recorded execution activity. They do not show exchange-wide demand, investor sentiment, instrument returns or price performance. Keeping instrument IDs and units visible avoids ambiguous tickers and incompatible quantity totals.”

**Other controls:** Side and instrument filters affect the selected analysis; current filtered values are shown in provenance. Next/Previous use server pagination for current data. Four demo instruments fit on one page, so disabled pagination is expected. Saved report tables contain the stored report result rather than new live pages.

### E. Client segments and the policy boundary — about one minute

**Do:** Open **Client segments** without entering a segment filter.

**Say:**

> “The system has effective-dated segment history, but analyst segment disclosure is withheld until a policy is approved. This protects against publishing small groups that could identify individuals. We are showing the implemented restriction, not bypassing it for the demonstration.”

The fixture includes clearly fictional `DEMO_RETAIL` and `DEMO_PRO` memberships. **Do not promise that their chart will be shown:** the approved disclosure policy is still missing. Do not enable `analytics.segment-disclosure-approved` merely to make the demo look complete.

Once policy permits disclosure, the implementation evaluates membership at fill execution time, retains an UNKNOWN bucket for missing history, and warns that clients can appear in multiple segments. Segment distinct counts need not add to the overall unique-client count. Account type is not a substitute for client segment.

### F. Issue, retrieve and amend a report — about three minutes

1. Return to **Overview** with September/UTC and no filters. Check provenance and totals.
2. Click **Issue monthly report**. Copy the report ID shown in the issued-report section.
3. Explain that filters, timezone, metric version, dataset, totals and completeness are preserved.
4. Click **Refresh analysis** to return to the current dataset. Paste the copied ID in **Saved report ID**, then click **Open saved report**.
5. Show the stored version and the same totals. Use the other tabs to inspect its saved tables.
6. Optional: enter amendment reason `Demo amendment: verifying versioning; source data unchanged.` Click **Create amended version from latest data**. Show the new version's link to the original. Since no source evidence changed, unchanged totals are expected.
7. Open the original ID again to demonstrate that it was not overwritten.

**Say:**

> “An issued report is a saved result, not a fresh query with an old month selected. Later evidence should produce an explicit amendment, preserving the original and the reason for correction.”

The **Report** tab uses the overview/report presentation and explains issuance. There is no separate printable report designer or download/export button in this implementation. Do not imply that exports, scheduled delivery, or stakeholder approval are complete. A report may preserve an incomplete dataset; issuing it does not certify completeness or approve proposed definitions.

## 5. Feature and rationale reference

| Feature | Business question or risk addressed |
| --- | --- |
| Separate user/staff sign-in | Clear entry paths; backend roles determine actual access. |
| Order filters and pagination | Find a specific operational record without unbounded queries. |
| Direct investigation lookup | Explain recorded lifecycle independently of analytics publication. |
| Timeline, fills, cash and holding movements | Reconcile the decision and financial effects; avoid inferred history. |
| User lookup and multiple accounts | Investigate the right account; distinguish current snapshot from historical evidence. |
| Claim, accept, notes, resolution | Explicit ownership and an accountable case workflow. |
| Release, reassignment, version conflicts | Handover and concurrent-investigator protection. |
| Native-currency amounts and quantity units | Avoid false combined totals and silent conversions. |
| Fill-based measures and cohort rejection rate | Count executions and submissions at the correct grains. |
| Dataset/cutoff/metric version and warnings | Explain reproducibility, freshness and source limitations. |
| Matching chart tables | Exact values, accessibility and easier reconciliation. |
| Segment withholding | Respect the unresolved disclosure-policy boundary. |
| Saved reports and amendments | Preserve issued evidence when later source records change. |
| OLAP isolation and bounded queries | Keep reporting failures and load from becoming trading queries. |

## 6. What to say when something looks wrong

| Observation | Presenter response and recovery |
| --- | --- |
| Empty dashboard | “The default month is not our demo period.” Select September 2026/UTC and clear filters. |
| Missing demo case | It may have been resolved in rehearsal. Filter RESOLVED to inspect it, or restart the disposable launcher for a fresh workflow. |
| Stale-data label | “This publication is older than the configured threshold.” Explain the timestamp; do not call a status refresh a data refresh. Restart the demo to publish fresh data. |
| INCOMPLETE / Unknown | Show coverage. Older seed evidence lacks fields; do not remove the warning or invent values. |
| Segment withheld | Expected until policy approval. Present the restriction and explain the intended dimension. |
| Dataset changed conflict | Refresh the entire analysis so panels use one version. |
| Invalid timezone or huge range | Correct the input. Backend validation and query limits are intentional. The month control normally keeps ranges bounded. |
| Case conflict or stale version | Reload the case and review its latest owner/status before acting. |
| Wrong staff workspace | Sign out and use the intended account via Staff sign-in. Do not edit local storage to create permissions. |
| Failed reporting query | Explain that unavailable is different from zero. Trading should not query OLTP as an analytics fallback. Use the regression suite to demonstrate failure isolation rather than stopping databases during the live presentation. |
| Demo launcher fails | Read the printed temporary log directory. Check Docker, dependencies and ports 14300/18182/14100, then retry after the previous demo stops. |

## 7. Claims to avoid and decisions still pending

Do not claim: production-ready legal retention; an approved small-segment threshold; formally approved metric definitions or production performance targets; approved BR-18 selection; live exchange sentiment; automatic refunds; a generic support inbox; a staff role-management UI; a dedicated audit-record browser; report export; or that this demo placed live market orders.

Explain instead that the demonstration covers implemented workflows and explicit boundaries. The legal retention policy, disclosure policy, final BR-18 approval, metric review, and measured production trading-impact/freshness targets still need stakeholder sign-off. Case history and investigation evidence are visible; some administrative/audit capabilities exist only through protected backend endpoints.

## 8. Five-minute version and closing

| Time | Show | Say |
| --- | --- | --- |
| 0:00–0:30 | User/staff choice; admin sign-in | “Separate entry paths; permissions come from the server.” |
| 0:30–1:30 | Investigate 800101 | “Two fills, one order, GBP 1,012 gross plus GBP 2 fees.” |
| 1:30–2:30 | Open demo dispute, claim, accept, note, resolve | “The owner records a reasoned decision against the evidence.” |
| 2:30–3:15 | Analyst sign-in; September/UTC Overview | “Six fills, five executed orders, three clients across four active accounts.” |
| 3:15–4:00 | Instruments and coverage | “Currencies and units stay separate; missing evidence is visible; segment disclosure is withheld.” |
| 4:00–5:00 | Issue and reopen report | “The issued version preserves its totals and provenance.” |

**Closing script:**

> “The admin workspace explains a customer's recorded trade and tracks the investigation. Analytics turns recorded activity into a versioned monthly report. Both show the evidence and its limits, while the customer continues to use a separate trading workspace.”

## Supporting files

- [Synthetic fixture](../scripts/fixtures/admin-analytics-demo.sql)
- [Full connection launcher](../scripts/verify-merged-connection.sh)
- [Independent demo assertions](../scripts/verify-merged-connection.cjs)
- [Metric dictionary](reporting-metric-contract.md)
- [Verification and database compatibility](merged-dashboard-verification.md)
- [Admin stakeholder decisions](admin-stakeholder-decisions.md)
- [Analytics release checklist](analytics-release-checklist.md)
