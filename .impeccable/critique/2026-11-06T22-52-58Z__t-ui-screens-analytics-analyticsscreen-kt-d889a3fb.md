---
target: reports page and navigation tabs
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt"
target_fingerprint: "sha256:9ea444bebcf895802bfe4f7112562f6f968f24f796512d6cec953a86080c3bf7"
target_path: /Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt
timestamp: 2026-11-06T22-52-58Z
slug: t-ui-screens-analytics-analyticsscreen-kt-d889a3fb
---
Method: design review only (detector not applicable, native platform)
Scope: Reports screen (AnalyticsScreen + charts) and app navigation (NavigationTab / NavGraph). Evidence: emulator Pixel Fold class, folded (compact) and unfolded (wide rail), light + dark, font scale 1.0 and 1.3.

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Chart readouts and period label are clear; nothing says which sections ignore the period selector |
| 2 | Match System / Real World | 2 | "Income Trend 0.0%", "Burn rate", and three names for one concept |
| 3 | User Control and Freedom | 3 | Period nav + Today work; chart selection has no reset besides re-tap |
| 4 | Consistency and Standards | 2 | Transactions / Records / Movements; two different "daily" averages on one screen |
| 5 | Error Prevention | 3 | Future periods disabled; future days drawn as stubs |
| 6 | Recognition Rather Than Recall | 3 | Labels on every tab; charts give no affordance that they are tappable |
| 7 | Flexibility and Efficiency | 2 | Category slice / top expense do not lead to filtered Records; tab reselect does not scroll to top |
| 8 | Aesthetic and Minimalist Design | 2 | Nine equal-weight cards, repeated facts (avg/day x2, savings rate x2, top category x2) |
| 9 | Error Recovery | 3 | Empty-period state and export error snackbar exist |
| 10 | Help and Documentation | 2 | Metrics like savings rate, trend, burn rate are unexplained |
| **Total** | | **25/40** | **Acceptable** |

Design specificity: Mostly authored. Money roles (jade/cochineal), category shape badges inside chart legends, and the añil hero slab make Reports recognisably BoxWallet. Structure is generic dashboard-stack: identical rounded cards in a column, the same order any finance app uses. Missed opportunity: the loan-in-installments reality (product positioning) is absent from Reports entirely; no "owed this period / installments paid" view.
Deterministic scan: not applicable (native platform).

Priority issues
1. [P1] Two time scopes under one period selector. Hero/categories/pattern/top use the selected period; Cash flow, Financial Health and Insights use the last 12 months. Result: "Avg. per day $89.36" (period) and "Daily Spending $3.73" (12 months) on the same screen; Income/Expense Trend show 0.0% because the first half of the 12-month window is empty. Fix: split the page into "This period" and "Last 12 months" with a visible divider; delete the Daily Spending tile; show "Not enough history" instead of 0.0% when either half is empty. -> /impeccable clarify, /impeccable layout
2. [P1] One concept, three names: tab "Records/Registros", Home header "Recent Transactions/Transacciones", FAB "Add Transaction", search and Reports "Movements/Movimientos". Fix: pick "Movements / Movimientos" everywhere including the tab label. -> /impeccable clarify
3. [P2] Flat hierarchy and repetition. Nine cards of equal weight; avg/day in hero and pattern; savings rate in ring and insight; top category in donut and insight. Fix: order by question (How did I do -> Where -> When -> Biggest -> 12-month trend), move insights inline as one-line callouts under the section they describe, drop the standalone Insights card. -> /impeccable distill
4. [P2] Navigation icons are ambiguous: Reports uses AreaChart (reads as a landscape), Loans uses Payments (reads as cash in general). Fix: Reports -> Insights or DonutLarge; Loans -> Handshake / RequestQuote. Tab reselect should scroll the list to top. -> /impeccable polish
5. [P2] Controls overhead: frequency row + navigator row + top-bar subtitle all state the period (~130dp before content on a phone); Daily view yields an almost empty page. Fix: drop the subtitle or the navigator label duplicate, consider removing Daily or folding frequency into the navigator's picker. -> /impeccable layout

Persona red flags
- Casey (one-handed phone): period chevrons and frequency toggles sit at the top of a long scroll; after scrolling to Cash flow, changing month means scrolling back up.
- Sam (TalkBack): pattern and cash-flow canvases expose only the currently selected value; no way to step through days/months; cash-flow bars distinguish income/expense by color only (readout has signs).
- Project persona, installment payer: opens Reports to see what is owed this month; finds nothing about loans or installments.

Minor observations
- Dark theme: the ranked ramp lerps toward surface, so the lowest-ranked legend bars almost vanish against the track.
- Font scale 1.3: "24% vs previous" truncates to "24% vs previ…" in both flow tiles.
- Cash flow shows Jan-Aug empty when history is short; trim leading empty months.
- Spending pattern is dominated by one spike; the rest of the month is stubs. Consider a cumulative line vs. last period for monthly view.
- Wide rail: large gap between the FAB header and the first destination; search lives in each top bar but not in the rail.
- Download icon for export; a share/export glyph matches the actual action (share sheet).

Questions to consider
- What if Reports answered "Am I on track this month?" in one sentence above the hero?
- Should loans/installments due appear in Reports, since they are the product's positioning?
- Does Daily frequency earn its place?
