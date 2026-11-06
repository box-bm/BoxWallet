---
target: reports page and navigation tabs
total_score: 32
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt"
target_fingerprint: "sha256:ae5d68b72d007b0addfe0bac7e29cc718964bdeaa0dd7bfef5f64afbb66b80d6"
target_path: /Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt
timestamp: 2026-11-06T23-34-01Z
slug: t-ui-screens-analytics-analyticsscreen-kt-d889a3fb
---
Method: design review only (detector not applicable, native platform)
Scope: Reports + navigation on feat/reports-critique-round-3 (PR #19, unmerged). Evidence: Pixel Fold emulator unfolded, dark theme; folded light from round 3.

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Pills, readouts and scope divider clear |
| 2 | Match System / Real World | 3 | "vs previous" compares 6 days of November with all of October |
| 3 | User Control and Freedom | 3 | Reselect, period nav, filtered drill-ins |
| 4 | Consistency and Standards | 3 | Inactive bars turn muddy brown/olive in dark theme |
| 5 | Error Prevention | 3 | Future periods blocked |
| 6 | Recognition Rather Than Recall | 3 | Labelled tabs, chart readouts |
| 7 | Flexibility and Efficiency | 4 | Category, installment, expense rows drill in, period-aware |
| 8 | Aesthetic and Minimalist Design | 4 | Each section answers a distinct question |
| 9 | Error Recovery | 3 | Empty-period state, export error |
| 10 | Help and Documentation | 3 | Savings ring and trends now captioned |
| **Total** | | **32/40** | **Good** |

Priority issues
1. [P1] In-progress period compared with a full one: on Nov 6, "Expense ↓16% vs previous" sets 6 days of November against all of October, so spending almost always looks down and income swings. Fix: while the period is in progress, compare with the same number of elapsed days of the previous period and say "vs same point last month". -> /impeccable clarify
2. [P2] Dark theme muddy inactive bars: expense/income at 40% alpha over the dark card read brown and olive (Spending pattern, Cash flow). Fix: blend toward the surface (lerp) instead of alpha. -> /impeccable polish
3. [P3] Trend caption shows when both trends say "Not enough history". Fix: hide it until at least one trend has a value. -> /impeccable distill
4. [P3] Wide layout: right column ends well above the left (Largest expenses vs categories), leaving a dead block before the 12-month divider. Fix: move Largest expenses under installments only when it balances, or let the shorter column take the concentration note. -> /impeccable layout
