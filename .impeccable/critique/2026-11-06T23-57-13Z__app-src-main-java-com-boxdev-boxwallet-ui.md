---
target: whole app
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/bmanzo/Developer/BoxWallet/app/src/main/java/com/boxdev/boxwallet/ui"
timestamp: 2026-11-06T23-57-13Z
slug: app-src-main-java-com-boxdev-boxwallet-ui
---
Method: design review only (detector not applicable, native platform)
Scope: whole app on main (382e929). Evidence: Pixel Fold emulator folded, light: Home, Movements, Loans, Loan detail, New Loan, New Movement, Search (empty + results), Settings; Reports from earlier rounds (wide/dark).

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Overdue chips and dates clear; loan detail hides which installments are overdue |
| 2 | Match System / Real World | 2 | Loans typed "Expense/Income"; "Cleared" is accounting jargon |
| 3 | User Control and Freedom | 3 | Back, reselect, global search, filters |
| 4 | Consistency and Standards | 2 | Reports says To pay/To collect, Loans says Expense/Income; overdue states differ per screen |
| 5 | Error Prevention | 3 | Required fields gate Create/Save |
| 6 | Recognition Rather Than Recall | 3 | Category badges, labelled tabs, search suggestions |
| 7 | Flexibility and Efficiency | 3 | Global search, drill-ins, filters |
| 8 | Aesthetic and Minimalist Design | 3 | Heavy loan cards repeated on Home; paid loan keeps full weight |
| 9 | Error Recovery | 3 | Empty states present |
| 10 | Help and Documentation | 3 | Empty states and captions explain |
| **Total** | | **28/40** | **Good** |

Priority issues
1. [P1] Loan direction is ambiguous: New Loan asks Expense or Income, detail shows an "Income" chip, cards show nothing. PRODUCT.md promises owed/paid states unambiguous at a glance. Fix: "I owe / Owed to me" everywhere; cards and detail label remaining as "Left to pay / Left to collect". -> /impeccable clarify
2. [P2] Loan detail hides overdue installments: #3 (36 days overdue) is styled "Next payment" purple, #4 overdue looks normal. Fix: paid/open/overdue states as in Reports. -> /impeccable polish
3. [P2] Paid progress turns red when a loan is overdue (cards and ring), so what was paid reads as the problem. Fix: progress keeps its money role; overdue lives in chip and date line. -> /impeccable polish
4. [P2] Loans tab has no totals and Home repeats full loan cards; paid loans keep full-size cards. Fix: To pay / To collect summary on Loans, compact rows on Home, completed loans collapsed. -> /impeccable distill
5. [P3] "Cleared" toggle in New Movement is jargon; FAB covers the last Home row amount at rest. -> /impeccable clarify, /impeccable polish

Minor
- Movements day headers repeat the current year.
- Usage Analytics uses the Reports insights icon and sits under Security.
- PRODUCT.md still names the tab "Records".
