---
target: reports page and navigation tabs
total_score: 31
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 0
target_identity: "file:/Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt"
target_fingerprint: "sha256:727244996ebf6311e4c8451c89ef7f1efe013dff51d1b967f34c1a925bfc1aca"
target_path: /Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt
timestamp: 2026-11-06T23-25-34Z
slug: t-ui-screens-analytics-analyticsscreen-kt-d889a3fb
---
Method: design review only (detector not applicable, native platform)
Scope: Reports + navigation on main after #17 and #18. Evidence: Pixel Fold emulator folded; Daily, Monthly, Yearly.

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Count pills clear; every open pill turns red once anything is overdue |
| 2 | Match System / Real World | 4 | Overspent/Saved labels, To pay/To collect read like a person talks |
| 3 | User Control and Freedom | 3 | Category link leaves the Reports period behind |
| 4 | Consistency and Standards | 3 | Overdue tint on non-overdue pills; same loan listed twice |
| 5 | Error Prevention | 3 | Future periods blocked |
| 6 | Recognition Rather Than Recall | 3 | Labelled tabs, readouts on every chart |
| 7 | Flexibility and Efficiency | 3 | Category, installment and expense rows all drill in |
| 8 | Aesthetic and Minimalist Design | 4 | Each view says something different; no repeated charts |
| 9 | Error Recovery | 3 | Empty-period state, export error |
| 10 | Help and Documentation | 2 | Savings-rate ring and trends still unexplained |
| **Total** | | **31/40** | **Good** |

Priority issues
1. [P2] Overdue tint on all open pills: 3 overdue of 5 open, all 5 tinted red. Fix: per-installment state (paid / open / overdue). -> /impeccable polish
2. [P2] Savings ring and trends unexplained; two savings figures (82% period, 77% 12-month) on one page. Fix: one-line definition under the ring; trend notes say what they compare. -> /impeccable clarify
3. [P3] Same loan repeated in installments list. Fix: group by loan with count. -> /impeccable distill
4. [P3] Category link drops the period. Fix: pass the Reports date range with the category. -> /impeccable polish
