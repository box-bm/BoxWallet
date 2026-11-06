---
target: reports page and navigation tabs
total_score: 29
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt"
target_fingerprint: "sha256:55ef378a88812ea1f61dddf50ab71760974336c04a63b6139d66cb0cdc37ba19"
target_path: /Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt
timestamp: 2026-11-06T23-14-29Z
slug: t-ui-screens-analytics-analyticsscreen-kt-d889a3fb
---
Method: design review only (detector not applicable, native platform)
Scope: Reports + navigation after PR #17. Evidence: Pixel Fold emulator folded/unfolded, light/dark, font 1.0/1.3; Daily, Monthly, Yearly views.

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Scope divider and readouts clear; installments bar at 0% reads as empty line |
| 2 | Match System / Real World | 3 | "Net Savings −$350"; "Excellent Savings!" + caption fragment reads broken |
| 3 | User Control and Freedom | 3 | Tab reselect returns to root/top; period nav solid |
| 4 | Consistency and Standards | 3 | Naming unified; installments total mixes money owed to you and by you |
| 5 | Error Prevention | 3 | Future periods blocked |
| 6 | Recognition Rather Than Recall | 3 | Labelled tabs, clearer icons |
| 7 | Flexibility and Efficiency | 3 | Installments and largest expenses open records; categories still don't open filtered Movements |
| 8 | Aesthetic and Minimalist Design | 3 | Repetition removed; Yearly view shows two near-identical bar charts |
| 9 | Error Recovery | 3 | Empty period state, export error snackbar |
| 10 | Help and Documentation | 2 | Verdict lines help; savings rate and trend still unexplained |
| **Total** | | **29/40** | **Good** |

Design specificity: Now product-specific. Installments due in Reports reflects the loan-in-installments positioning; verdict line speaks plainly; money roles and category badges hold. Daily view remains the generic part.
Deterministic scan: not applicable (native platform).

Priority issues
1. [P1] Installments total mixes directions: "$0.00 of $450.00" adds a $150 installment owed to the user and a $300 one the user owes. Fix: split into "To pay" and "To collect" amounts; progress by count. -> /impeccable clarify
2. [P2] Daily view is noise: day-over-day deltas (+471%, −100%), a single-slice full donut, "No income recorded" on an ordinary day. Fix: hide deltas and the donut for Daily; show the day's movements list instead of "Largest expenses". -> /impeccable distill
3. [P2] Yearly duplicates the 12-month cash flow: "Monthly Breakdown" for the current year is nearly the same chart as Cash flow below. Fix: for Yearly show net per month (one diverging series) or rank months by spend. -> /impeccable layout
4. [P2] Copy seams: hero label "Net Savings" over a negative number; health title "Excellent Savings!" followed by the caption fragment "of your income kept over the last 12 months". Fix: label "Net" (or "Overspent" when negative); caption "Savings rate · last 12 months". -> /impeccable clarify
5. [P3] Installments wavy bar at 0% is a bare line with a dot; category rows/slices don't open Movements filtered to that category. -> /impeccable polish

Persona red flags
- Installment payer: sees "$450 due" when they actually owe $300 and are owed $150.
- Casey: Daily view percentages swing wildly day to day, no stable signal.
- Sam: chart previous/next actions now exist; donut slices are still canvas-only (legend rows cover it).

Minor observations
- Hero still follows the big-number template; justified by "balance is the truth".
- Rail still lacks search on wide; top bar carries it.

Questions
- Should Daily exist as a frequency, or become "Today" inside Weekly?
- Should installments owed to the user count toward income in the period summary?
