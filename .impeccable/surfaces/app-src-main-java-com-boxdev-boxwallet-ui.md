---
version: 1
slug: "app-src-main-java-com-boxdev-boxwallet-ui"
primary_target: "app/src/main/java/com/boxdev/boxwallet/ui"
related_targets: []
---

# Surface brief: BoxWallet app (all screens)

Scope: every in-app screen (Home, Records, Loans + detail, Reports, Settings + sub-screens, sheets, Onboarding, Lock). Mode: **Operate**. Platform: Android, Jetpack Compose, phones + foldables (folded/unfolded) + tablets.

Audience/job: individuals in Central America logging expenses/incomes in seconds, checking the month's balance, and tracking installment loans. Constraints: all features stay; nothing else is sacred (owner). Brand palette default, dynamic color opt-in.

## Direction contract

THESIS: Material 3 Expressive played straight and fully committed, pinned by the owner. BoxWallet's screens are a working money app first. It refuses the category default: a grey list with a green accent and a hero card where every surface weighs the same.

OWN-WORLD: Añil indigo primary (#434DC5) for the shell and selection, marigold tertiary (#FEA600 container) for attention, jade income and cochineal expense as their own semantic roles, never `error` for spending. Indigo-tinted neutrals. Roboto Flex for display/headline numerals: wide, heavy, tabular. System Roboto for body. Expressive shape scale with large radii. Category badges are drawn as MaterialShapes polygons.

STORY: open the app, read the balance and the month's in/out before anything else. Log a movement from one obvious button. See what is owed and what comes next. Every number is legible, signed, and colored by meaning, with shape and sign as backups for color.

FIRST VIEWPORT (Home, compact): a large flexible top bar with the app name, then a primary-container balance panel that owns the top third: the balance set in Roboto Flex display, then income/expense as a two-segment proportion bar with jade/cochineal tiles. Recent movements follow in a connected list group, then active loans with wavy progress. FAB menu bottom-end. Short navigation bar. Unfolded: wide navigation rail with the FAB in its header, Home in two panes (balance + loans | movements), Loans list-detail.

FORM: Material 3 Expressive (owner-pinned), #1 on the list. seed: none (direction pinned by brief; concept-seed not run). Signature move: **category shape tokens**. Each category gets a stable MaterialShapes silhouette and tone across lists, pickers and chart legends, and it morphs on press. Installments use wavy progress.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
