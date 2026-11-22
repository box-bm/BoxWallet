---
name: BoxWallet
description: A private, on-device money app for Central America, drawn in Material 3 Expressive with añil indigo, marigold, and money-coded jade and cochineal.
colors:
  primary: "#434DC5"
  on-primary: "#F3F1FF"
  primary-container: "#8E98FF"
  on-primary-container: "#00077E"
  secondary: "#5B5877"
  secondary-container: "#DBD6FB"
  on-secondary-container: "#4C4967"
  tertiary: "#805100"
  tertiary-container: "#FEA600"
  on-tertiary-container: "#4E3000"
  income: "#006D3E"
  income-container: "#9BF6BA"
  on-income-container: "#00210F"
  expense: "#A93441"
  expense-container: "#FFDADA"
  on-expense-container: "#40000B"
  error: "#B41340"
  error-container: "#F74B6D"
  on-error-container: "#510017"
  surface: "#F8F5FD"
  on-surface: "#2E2E34"
  on-surface-variant: "#5C5B61"
  surface-container-low: "#F2EFF8"
  surface-container: "#E9E7F0"
  surface-container-high: "#E4E1EB"
  surface-container-highest: "#DEDBE6"
  outline: "#77767D"
  outline-variant: "#AEACB3"
  inverse-surface: "#0E0E13"
typography:
  money-hero:
    fontFamily: "Roboto Flex (wdth 124, opsz 120), Roboto, sans-serif"
    fontSize: "52px"
    fontWeight: 800
    lineHeight: "56px"
    letterSpacing: "-1.5px"
    fontFeature: "tnum"
  money-large:
    fontFamily: "Roboto Flex (wdth 120, opsz 96), Roboto, sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: "34px"
    letterSpacing: "-0.5px"
    fontFeature: "tnum"
  money-medium:
    fontFamily: "Roboto Flex (wdth 116, opsz 72), Roboto, sans-serif"
    fontSize: "18px"
    fontWeight: 600
    lineHeight: "24px"
    fontFeature: "tnum"
  money-small:
    fontFamily: "Roboto Flex (wdth 112, opsz 72), Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "20px"
    fontFeature: "tnum"
  display:
    fontFamily: "Roboto Flex (wdth 112), Roboto, sans-serif"
    fontSize: "57px"
    fontWeight: 700
    lineHeight: "64px"
    letterSpacing: "-1.2px"
  headline-large:
    fontFamily: "Roboto Flex (wdth 112), Roboto, sans-serif"
    fontSize: "32px"
    fontWeight: 700
    lineHeight: "40px"
    letterSpacing: "-0.4px"
  headline-small:
    fontFamily: "Roboto Flex (wdth 108), Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: "32px"
  title-large:
    fontFamily: "Roboto Flex (wdth 108), Roboto, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: "28px"
  title-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "24px"
    letterSpacing: "0.15px"
  body-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.5px"
  body-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0.25px"
  label-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
rounded:
  extra-small: "4px"
  small: "8px"
  medium: "12px"
  large: "16px"
  large-increased: "20px"
  extra-large: "28px"
  extra-large-increased: "32px"
  extra-extra-large: "48px"
  group-outer: "28px"
  group-inner: "4px"
  full: "9999px"
spacing:
  xxs: "4px"
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
  xxl: "32px"
components:
  balance-panel:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.money-hero}"
    rounded: "{rounded.extra-large-increased}"
    padding: "24px"
  transaction-row:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    rounded: "{rounded.group-inner}"
    padding: "16px"
  loan-card:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.extra-large}"
    padding: "20px"
  loan-card-selected:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    rounded: "{rounded.extra-large}"
    padding: "20px"
  amount-field:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.money-hero}"
    rounded: "{rounded.extra-large-increased}"
    padding: "16px 20px"
  search-field:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.extra-large-increased}"
    height: "56px"
  status-chip-active:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "0 12px 0 8px"
    height: "28px"
  status-chip-paid:
    backgroundColor: "{colors.income-container}"
    textColor: "{colors.on-income-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "0 12px 0 8px"
    height: "28px"
  status-chip-overdue:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "0 12px 0 8px"
    height: "28px"
  info-chip:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "5px 12px"
    height: "28px"
  settings-row:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-large}"
    rounded: "{rounded.group-inner}"
  form-save-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    height: "40px"
  section-header:
    textColor: "{colors.on-surface}"
    typography: "{typography.title-large}"
    height: "48px"
---

# Design System: BoxWallet

## Overview

**Creative North Star: "The Indigo Ledger"**

BoxWallet is Material 3 Expressive played straight: the platform's own components, motion springs, shape morphs and flexible app bars, committed to fully rather than skinned over. It is a working money app first. Every screen leads with a number, and every number is set in a wide, heavy, tabular Roboto Flex cut so columns of money line up and the balance reads from across a table. Expressiveness lives in three places only: the indigo balance slab, the category shape tokens, and the springs that move them. Everything else is calm tonal surfaces in indigo-tinted neutrals.

Density is moderate and touch-first: 48px minimum targets, 56px fields and primary buttons, lists built as connected groups rather than separated cards. The system refuses the category default of a grey list with a green accent and a hero card where every surface weighs the same; here the balance panel is the single heaviest object on Home, and lists sit visibly lower in the tonal stack.

The brand palette is the default look. Material You dynamic color is an opt-in that swaps the scheme roles; every component reads scheme roles, so the system survives the swap, while the money roles (income, expense) stay brand-fixed in both modes. The theme also honors Android's system contrast setting with medium and high contrast variants of both the scheme and the money roles.

**Key Characteristics:**
- Roboto Flex numerals, wide and heavy with tabular figures, for every amount.
- One indigo slab per screen at most; everything else is tonal surface-container layering, no custom shadows.
- Categories are identified by a stable MaterialShapes silhouette and tone, which relaxes to a circle on press.
- Income and expense are their own semantic roles (jade and cochineal), always signed with + or −.
- The Material 3 corner scale (4 to 48px, cards at 28) and connected list groups with round outer and tight inner corners.
- Content sits straight on the surface body in contained groups and cards, under one search bar; only the supporting pane of a two-pane layout gets a container.
- Adaptive by width class: short navigation bar and FAB menu when compact; navigation rail when wide; two panes from Expanded width.

## Colors

An añil-indigo shell with a marigold attention color, indigo-tinted neutrals, and two money roles that sit outside Material's scheme. Values below are the light standard-contrast scheme; dark and contrast variants are generated from the same HCT seeds (see the sidecar).

### Primary
- **Añil Indigo** (primary): the shell and selection color. Fills the balance panel slab, the form Save button, category-breakdown bars, active loan progress, the selected category ring, the text cursor, settings group titles and selected menu values.
- **Periwinkle Wash** (primary-container): FAB container, one of the four category tones.

### Secondary
- **Lilac Mist** (secondary-container): selection backgrounds (selected loan card, active navigation indicator), settings icon circles, empty-state polygons, one category tone.

### Tertiary
- **Marigold** (tertiary-container): attention without alarm. The "Active" loan status chip, the next unpaid installment row in loan detail, one category tone. Never a money direction.

### Money roles
- **Jade** (income / income-container): income amounts, the income share of the balance proportion bar, the "Paid" status chip, paid loan progress, the income line in the monthly trend chart.
- **Cochineal** (expense / expense-container): expense amounts, the expense share of the proportion bar, the expense line in the monthly trend chart.

### Neutral
- **Lavender Paper** (surface): the body of every screen, in every window size: tabs, secondary pages, full-screen forms and sheets. Navigation (bar and rail) sits one step up on surface-container, the same on phone and tablet.
- **Ink Plum** (on-surface): primary text and neutral amounts. **Slate Plum** (on-surface-variant) carries secondary text, labels and unfilled placeholder amounts.
- **Surface container family** (surface-container-low through -highest): the depth system. Low for a contained pane (the detail or editor pane beside a list in two-pane layouts) and the compact filter panel that folds open in Movements and Loans; base for the navigation bar and rail and for cards, list rows and groups on the body (`BoxTheme.cardColor`); high for form rows, the amount field, the search field and for cards inside a contained pane; highest for progress tracks, info chips and the neutral category tone. Surface-container-lowest is not used.
- **Outline Variant**: chart grid lines only.

### Named Rules
**The Spending Is Not An Error Rule.** Expenses use the cochineal expense role, never `error`. `error` is reserved for genuine faults and overdue loans, which are a real problem state.

**The Sign Carries It Rule.** Every directional amount carries a + or − (true minus, U+2212) and, where space allows, a trend icon. Color is the third signal, never the only one; on the indigo slab, amounts drop to on-primary and the sign alone carries direction.

**The One Slab Rule.** Primary as a filled surface is reserved for the Home balance panel. Other containers use the surface-container family or a container role.

## Typography

**Display Font:** Roboto Flex (variable; bundled), width-axis tuned per weight, falling back to Roboto
**Body Font:** system Roboto
**Numerals:** Roboto Flex at wide widths (112 to 124) with optical size and tabular figures

**Character:** Roboto Flex pushed wide and heavy gives headings and money a confident, almost poster-like voice; plain system Roboto keeps reading text native and quiet. The contrast between the two is the type system.

### Hierarchy
- **Money Hero** (800, 52px/56px, -1.5px, tnum): the Home balance and the amount being typed in a form. Auto-sizes down to 28px rather than wrapping or truncating.
- **Money Large** (700, 28px/34px, tnum): income and expense tiles, loan remaining amounts, the currency symbol beside a hero amount.
- **Money Medium** (600, 18px/24px, tnum): the default amount in list rows.
- **Money Small** (500, 14px/20px, tnum): secondary amounts (loan totals, category breakdown, the month's net).
- **Display / Headline** (Roboto Flex, 700 bold with negative tracking; headline-small and title-large at 600): screen titles in flexible app bars, sheet headers (headline-small), section headers (title-large).
- **Title Medium / Small** (Roboto, 600): card titles, settings group titles.
- **Body** (Roboto, 400, 16px and 14px): list row titles and supporting lines.
- **Label Large** (Roboto, 600, 14px): chip text, field labels, stat captions.

### Named Rules
**The Tabular Money Rule.** Any amount is rendered through AmountText or a MoneyType style; never set money in a body or title style.

**The Font Scale Rule.** Every size is sp and honors system font scale; amounts survive 130% by auto-sizing, not by clipping.

## Layout

Layout is driven by the window size, not the device or its orientation: there is no orientation lock. Three width classes, from the M3 breakpoints: Compact (under 600dp), Medium (600 to 839dp), Expanded (840dp and up). Phones in portrait and folded foldables are Compact. A phone in landscape rotates into the rail and panes rather than stretching the portrait layout: small phones land in Medium, Pixel-class phones are wide enough for Expanded and get two panes. Unfolded foldables and tablets are Expanded. Under 480dp of height (phone landscape) nothing may depend on a tall window: the collapsed rail drops its labels (the icons keep the names for accessibility), takes a small FAB and scrolls only if its destinations still don't fit.

- **Window and panes:** the whole window is surface; the navigation bar and rail are surface-container. Content is laid out in panes (`PaneRow`) that blend into the body: they set margins and widths but paint nothing. Margins are 16px on Compact and 24px on Medium and wider, applied once, with a 24px spacer between two panes. A fixed pane is 360dp beside a flexible one; otherwise two panes split 50/50. In two-pane layouts only the supporting pane is contained (`AppPane`: surface-container-low, 32px corners): the editor in Movements, the detail in Loans and the page in Settings. Home's two columns and Reports stay uncontained. Back only appears in single-pane layouts; selection is only shown in two-pane layouts, and an empty detail pane shows a placeholder.
- **Compact:** one pane, a ShortNavigationBar, and a FloatingActionButtonMenu at bottom-end whose action is contextual (Movements adds a movement, Home asks). Lists end with a 96px spacer to clear the FAB.
- **Medium:** one pane beside a collapsed WideNavigationRail with the FAB in its header.
- **Expanded:** the rail plus two panes where the screen has them; the rail is expanded (labels beside icons, extended FAB) at 1200dp and above.
- **Secondary destinations** (Settings and its pages, Search, a loan opened on its own, currency detail, the movement and loan forms) hide the navigation: the rail on wide windows and the bottom bar on compact, with a back button in their place. The FAB shows only on Home and Movements, in the bar layout and in the rail header alike. Wherever no bottom bar sits under them (rail layouts, secondary destinations), content ends above the system navigation bar.

| Screen | Compact | Medium | Expanded |
|---|---|---|---|
| Home | 1 pane: balance, recent movements, loans | 1 pane | 2 panes 50/50: balance and loans \| recent movements |
| Movements | 1 pane, filters fold open behind Tune; a tapped movement opens the full-screen form | same | list 360dp \| editor pane for the selected movement, placeholder with no selection, selected row highlighted; filters still fold open behind Tune |
| Loans (opened from Home) | list, then detail in the same pane with back | same | list-detail (`NavigableListDetailPaneScaffold`): list 360dp \| detail, placeholder with no selection, selected card highlighted |
| Reports | 1 pane | 1 pane | 1 pane with its two inner columns |
| Settings | list, then the page with back | same | list-detail: sections 360dp \| page (General, Currencies, Categories, Theme, Backup, Data) |
| Search | full screen | same | same, results up to 720dp |

A loan opened from Home, Search or Reports is its own destination and back returns to where it came from. Currency detail and changing the main currency stay full destinations over Settings. Folding, unfolding or rotating keeps the selection (detail in one pane if something was selected, the list if not) and the scroll position.

- **Readable measure:** settings pages and similar single-column content cap at 720dp and center.
- **Rhythm:** a 4px base with 8, 12, 16, 20, 24 and 32 as the working steps. 16px is the default gap between content blocks and the window margin on compact; 24px separates settings groups and panes and is the margin on wider windows; 20px is card padding.

## Elevation & Depth

The system is flat and tonal. No component declares a shadow; depth comes from the surface-container stack and from the single primary slab. A contained pane is set apart from the surface body by tone, not by a shadow. The only shadows are the stock Material elevations on FABs, menus and bottom sheets.

**The Tonal Stack Rule.** The stack is surface (body), surface-container-low (contained pane), surface-container (cards, rows, navigation), surface-container-high (fields, cards inside a contained pane), surface-container-highest (tracks, chips). To raise something, step it one level up; never add a shadow or a border to lift it. Cards read their tone from `BoxTheme.cardColor`, which steps up by itself inside a contained pane.

## Shapes

Expressive and soft, on the Material 3 corner scale token for token: extra-small 4, small 8, medium 12, large 16, large-increased 20, extra-large 28, extra-large-increased 32, extra-extra-large 48px. Loan cards, report sections, form surfaces and the compact filter panel use extra-large (28); the balance panel, the amount field, detail cards and the contained pane use extra-large-increased (32). Chips and buttons are full pills.

**The Optical Roundness Rule.** A nested container never repeats its parent's radius: inner radius = outer radius − padding (the income and expense tiles inside the 28px report summary use 12px).

Lists are connected groups: rows are separated by Material's segmented gap and share outer corners of the group (28px outer, 4px inner for form groups and the Loans totals tiles; Material segmented shapes for list items), so a group reads as one object. A lone row keeps the large outer radius instead of collapsing to small corners.

Category badges are MaterialShapes polygons (Cookie9Sided, Clover4Leaf, Sunny, Cookie6Sided, Gem, Flower, SoftBurst, Pentagon, Puffy, Arch), chosen stably per category. Empty states sit inside a 112px Cookie9Sided polygon on secondary-container.

**The Shape Is Identity Rule.** A category keeps one silhouette and one tone (primary-, tertiary-, secondary-container or surface-container-highest) everywhere: lists, pickers, chart legends, loan cards. Do not reassign shapes per screen or use them decoratively for things that are not categories (empty states and onboarding excepted).

## Components

### Balance Panel (signature)
The heaviest object in the app. A primary slab straight on the surface body, with 32px corners and 24px padding: a title-medium "Total Balance" caption at 86% on-primary, the balance in Money Hero, then the month's net (signed) and a 14px two-segment proportion bar (income-container | expense-container, 4px gap, outer ends 7px round, inner ends 3px). Below it, two flow tiles: a 10px color dot, a trend icon and label, and a signed Money Large amount in on-primary. The bar and share animate on the slow spatial spring.

### Category Badge (signature)
A 44px polygon (40px in charts) clipped from the category's MaterialShapes silhouette, filled with its tone, icon at 48% size. While pressed or selected it morphs to a circle on the fast spatial spring. Uncategorized movements fall back to a circle in the money container color with a trend icon.

### Lists and Rows
- **Transaction row:** a SegmentedListItem on surface-container; category badge leading; title one line; supporting line "Category · date" plus 14px icons for recurring, reminder and scheduled; signed Money Medium amount trailing in jade or cochineal.
- **Settings row:** SegmentedListItem on surface-container with a 40px tonal circle icon (secondary-container), chevron for navigation, switch for toggles; the whole row toggles and the row tint never changes with state.
- **Form rows:** 64px minimum rows on surface-container-high using the 28/4 group shape.

### Cards
- **Loan card:** the card tone (surface-container), 28px corners, 20px padding, 14px internal rhythm. Badge, title and status chip; remaining amount in Money Large; a LinearWavyProgressIndicator colored by status (primary active, jade paid, error overdue) on a surface-container-highest track, whose wave flattens when paid; next due date (error when overdue) and "n/m paid". Selected cards turn secondary-container.

### Chips
- **Status chip:** a 28px pill with a 16px icon and label-large text. Active is marigold, Paid is jade container, Overdue is error container. Icon plus label always, never color alone.
- **Info chip:** neutral pill on surface-container-highest for a category or loan direction.

### Inputs / Fields
- **Amount field:** the amount is the point of the form. Its header row carries the label at the start and, at the end, the currency as a quiet menu button (code plus a drop-down arrow on surface-container-highest) that opens the currency sheet; the amount stands alone below it. Surface-container-high, 32px corners, typed at Money Hero size and centered, currency symbol in Money Large before it; the ink animates from on-surface-variant (empty, "0.00") to the direction's money color. Error adds a 2px error border and error label.
- **Top search bar:** each tab opens with a collapsed M3 SearchBar on the surface body, above the content, in place of a top app bar and its search action: a 56px pill on surface-container-high, a leading search icon, the placeholder "Search BoxWallet" and one trailing action per screen. Home: Settings (the only way into Settings). Movements: Tune with a badge counting the active filters. Reports: Export. Search is global, so the bar belongs to the window: it spans the full content width in every tab, across both panes. It does not collapse on scroll; tapping it opens Global search. The tabs have no titles; "BoxWallet" lives only in the placeholder, and the Movements count ("N movements") is a line at the top of its list.
- **Global search:** one search for the whole app, opened from the top search bar. A full screen with a 56px pill TextField on surface-container-high (no indicator line), All / Movements / Loans scope chips, and results grouped into Loans and Movements. Matching ignores case and accents and also covers category names and amounts.
- **Choice rows:** connected ToggleButton groups (the Expressive replacement for segmented buttons); unselected on surface-container-highest. The income/expense choice in forms takes the money container colors.
- **Category picker:** a horizontal row of category badges; the chosen one morphs to a circle and gets a 2px primary ring.

### Buttons
- **Form Save:** a filled button with Expressive morphing shapes at the end of the form's top bar, and the only action there. It is never disabled: saving an incomplete form reports what is missing in line.
- **Destructive:** delete is not a top-bar action. In a form it is a text button in the error color with a delete icon, centered at the foot of the fields ("Delete Movement" / "Delete Loan"); confirm-delete dialogs use a filled error button.
- **FAB:** ToggleFloatingActionButton opening a FloatingActionButtonMenu (add movement, add loan); in the rail it is a FAB or extended FAB with a dropdown menu.

### Navigation and Chrome
ShortNavigationBar (compact) and WideNavigationRail (wide) with filled icons on the active destination and outlined icons otherwise. Destinations are Home, Movements (receipt) and Reports (insights); loans live inside accounts and Home. Tapping a tab always switches to it, even from a secondary destination; tapping the selected tab returns to its root and then scrolls it to the top. The product calls every income or expense a **movement / movimiento**; never "transaction" or "record" in UI copy. Tabs start with the top search bar instead of an app bar; settings pages and a standalone loan detail use large flexible app bars that collapse on scroll. Section headers are title-large with a text-button action at 48px height. Loading uses the Expressive LoadingIndicator.

### Forms
Creating or editing a movement or a loan is a full-screen form (M3 full-screen dialog pattern) on its own navigation destination (`transaction_form?id=`, `loan_form?id=`), opened from the FAB or rail header, from any movement row (Home, Movements, Search, Reports) and from a loan's edit action (in the Loans pane or on its own). It hides the navigation like any secondary destination.
- **Top bar:** ✕ (labelled "Close") at the start, the title ("New Movement" / "Edit Movement", "New Loan" / "Edit Loan") and a filled Save at the end, always enabled. When editing, delete sits at the foot of the form as an error text button.
- **In a pane:** on two-pane windows the Movements tab shows the same movement form as its editor pane (`embedded`): transparent on the contained pane, no ✕ (the list selection closes it), Save in its header. Choosing another row with unsaved edits asks "Discard changes?" first; saving or deleting returns the pane to its placeholder; folding carries the movement on in the full-screen form.
- **Body:** the form's fields in a scrolling column on surface (Lavender Paper), capped at 640dp and centered on wide windows, with the window margin at the sides. The form pads itself above the keyboard (the activity resizes rather than pans), so the focused field scrolls into view and the top bar with Save stays on screen. A failed save is reported in error text above the fields, announced politely, and the form scrolls back to it.
- **Leaving:** Save and a confirmed delete return to where the form was opened, once. ✕ and system back close straight away when nothing changed (back then uses the predictive-back animation); after an edit they ask "Discard changes?" (Keep editing / Discard).
- **Motion:** the form enters by rising an eighth of its height while fading in, and leaves the same way.

### Loans
A loan's direction is always said in words, never as "expense" or "income": "I owe" / "Owed to me" on the form, "Left to pay" / "Left to collect" on cards, rows and detail, "Total Paid" / "Total Collected" in detail. The loans list opens with what is outstanding by direction ("You owe" / "Owed to you", never netted), as two connected tiles in the card tone with the amounts in Money Large. Progress shows what has been settled, so it stays primary and never turns red; overdue is carried by the status chip and the due-date line. Installments in detail use the same paid / open / overdue rule as Reports (`installmentState`): overdue rows sit on the error container with "Overdue since", and "Next payment" marks the first installment still ahead. Home lists loans as one-line rows (`LoanRow`); full cards live on the loans list, where paid-off loans fold into "Completed (n)".

### Reports
The period selector is a connected toggle group where only the selected period is filled with primary; the period navigator under it is quiet (a surface-container-high pill for the date, plain icon buttons for previous and next). Two time scopes, never mixed: everything above the "Last 12 months" divider follows the period selector (summary with a one-line verdict, categories, spending pattern, largest expenses, installments due); everything below it always covers the last 12 months (cash flow, savings-rate ring, income/expense trend). Insights sit as one-line notes inside the section they explain, not in a card of their own. A trend with an empty half-window reads "Not enough history", never 0.0%. While the selected period is still in progress, "vs previous" compares the same number of elapsed days of the previous period and says so ("vs same point last month"); a partial period is never set against a full one. On wide layouts the period sections are split between two columns by measured height, so the columns end level. The Daily view drops shares, rankings and day-over-day changes and lists the day's movements instead. Yearly shows net per month as diverging bars (saved above the line, overspent below), so it never repeats the 12-month cash flow. The summary label carries the direction ("Net Savings" or "Overspent"). Installments never add money the user owes to money owed to them: "To pay" and "To collect" stay separate, and progress is a count of installments, one pill each, coloured by its own state (paid, open, overdue). Unpaid installments are listed once per loan ("3 open · 2 overdue since Oct 1"). A focused category offers "See <category> movements", which opens the Movements tab filtered to that category and the period on screen. The savings ring is captioned with what it measures, and the trend values say what they compare. Inside a section, stats are plain label/value pairs, not nested tiles.

### Charts
De-emphasised series (unselected months or days) are mixed toward the card surface, never made transparent, so income and expense keep their hue in dark theme.

Charts grow in on the slow spatial spring (or appear at once when animations are off), and you tap or drag them to inspect a value; the readout above the plot shows the value being inspected.
- **Cash flow:** paired round-topped income/expense bars per month in their money roles, on a compact-labelled axis (`$2.5k`) with a solid baseline and dashed outline-variant guides. The inspected month sits on a surface-container-highest column, and the other months drop to 42% strength.
- **Spending pattern:** expense per day as round-topped cochineal bars with a dashed daily-average line and its value in a pill. Days that have not happened yet show as 3px stubs.
- **Expenses by category:** a donut ranked in a single primary hue (largest slice darkest, stepping toward the surface; "Other" in outline) with the total in its hole. Below it, a ranked legend where each category keeps its shape badge and name, so hue never carries identity alone. The top five categories show and the rest fold into "Other".

### Motion
MaterialExpressiveTheme with the expressive motion scheme. Fast spatial springs for touch feedback (badge morph), slow spatial for data (proportion bar, loan progress, category bars), default effects for color changes, fast effects for exits. Lock and onboarding screens check Remove-animations and drop their decorative motion.

## Do's and Don'ts

### Do:
- **Do** set every amount through AmountText or a MoneyType style, with tabular figures and a + or − for directional money.
- **Do** read colors from scheme roles and `BoxTheme.money`, so dynamic color, dark mode and system contrast all keep working.
- **Do** build lists as connected segmented groups on surface-container, using segmentShapes or GroupShapes for corners.
- **Do** give every category its CategoryBadge wherever the category appears.
- **Do** branch layout on `BoxTheme.layout` (width class, navigation kind, two panes, margin): the rail when wide, two panes from Expanded, the short bar and FAB menu when compact.
- **Do** put content straight on the surface body through `PaneRow`, in contained groups and cards; contain only the supporting pane of a two-pane layout with `AppPane`.
- **Do** take card and row containers from `BoxTheme.cardColor`.
- **Do** pair status color with an icon and a label.

### Don't:
- **Don't** use `error` or red for ordinary spending; expenses use the cochineal expense role.
- **Don't** add shadows or borders to lift a surface; step up the surface-container stack instead.
- **Don't** wrap a whole screen in a container or use surface-container-lowest; a pane on a phone is the body itself.
- **Don't** nest a container with the same radius as its parent.
- **Don't** fill a second surface with primary on the same screen as the balance panel.
- **Don't** tell categories apart by hue alone in charts; use the shape badge and name.
- **Don't** hard-code hex colors in screens or view models; all color lives in the theme.
- **Don't** set money in Roboto body or title styles, or let amounts clip at large font scales; auto-size them.
