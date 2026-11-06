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
  extra-small: "8px"
  small: "12px"
  medium: "20px"
  large: "28px"
  extra-large: "36px"
  group-outer: "24px"
  group-inner: "6px"
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
    rounded: "{rounded.extra-large}"
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
    rounded: "{rounded.large}"
    padding: "20px"
  loan-card-selected:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    rounded: "{rounded.large}"
    padding: "20px"
  amount-field:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.money-hero}"
    rounded: "{rounded.extra-large}"
    padding: "16px 20px"
  search-field:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.extra-large}"
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
  sheet-primary-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.full}"
    height: "56px"
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
- Generous expressive radii (8 to 36px) and connected list groups with round outer and tight inner corners.
- Adaptive by width class: short navigation bar and FAB menu when compact; wide navigation rail and multi-pane layouts when wide.

## Colors

An añil-indigo shell with a marigold attention color, indigo-tinted neutrals, and two money roles that sit outside Material's scheme. Values below are the light standard-contrast scheme; dark and contrast variants are generated from the same HCT seeds (see the sidecar).

### Primary
- **Añil Indigo** (primary): the shell and selection color. Fills the balance panel slab, the sheet primary button, category-breakdown bars, active loan progress, the selected category ring, the text cursor, settings group titles and selected menu values.
- **Periwinkle Wash** (primary-container): FAB container, one of the four category tones.

### Secondary
- **Lilac Mist** (secondary-container): selection backgrounds (selected loan card, active navigation indicator), settings icon circles, empty-state polygons, one category tone.

### Tertiary
- **Marigold** (tertiary-container): attention without alarm. The "Active" loan status chip, the next unpaid installment row in loan detail, one category tone. Never a money direction.

### Money roles
- **Jade** (income / income-container): income amounts, the income share of the balance proportion bar, the "Paid" status chip, paid loan progress, the income line in the monthly trend chart.
- **Cochineal** (expense / expense-container): expense amounts, the expense share of the proportion bar, the expense line in the monthly trend chart.

### Neutral
- **Lavender Paper** (surface): app background and top bars.
- **Ink Plum** (on-surface): primary text and neutral amounts. **Slate Plum** (on-surface-variant) carries secondary text, labels and unfilled placeholder amounts.
- **Surface container family** (surface-container-low through -highest): the depth system. Low for side panes (Records filters, Loans detail pane); base for list rows, loan cards, settings rows and the navigation rail; high for form rows, the amount field and the search field; highest for progress tracks, info chips and the neutral category tone.
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
- **Money Hero** (800, 52px/56px, -1.5px, tnum): the Home balance and the amount being typed in a sheet. Auto-sizes down to 28px rather than wrapping or truncating.
- **Money Large** (700, 28px/34px, tnum): income and expense tiles, loan remaining amounts, the currency symbol beside a hero amount.
- **Money Medium** (600, 18px/24px, tnum): the default amount in list rows.
- **Money Small** (500, 14px/20px, tnum): secondary amounts (loan totals, category breakdown, the month's net).
- **Display / Headline** (Roboto Flex, 700 bold with negative tracking; headline-small and title-large at 600): screen titles in flexible app bars, sheet headers (headline-small), section headers (title-large).
- **Title Medium / Small** (Roboto, 600): card titles, settings group titles, button text in sheets.
- **Body** (Roboto, 400, 16px and 14px): list row titles and supporting lines.
- **Label Large** (Roboto, 600, 14px): chip text, field labels, stat captions.

### Named Rules
**The Tabular Money Rule.** Any amount is rendered through AmountText or a MoneyType style; never set money in a body or title style.

**The Font Scale Rule.** Every size is sp and honors system font scale; amounts survive 130% by auto-sizing, not by clipping.

## Layout

Layout is driven by three width classes: Compact (under 600dp), Medium (600 to 839dp), Expanded (840dp and up). Phones and folded foldables are Compact; unfolded foldables and tablets are wide.

- **Compact:** single column with 16px side margins, a ShortNavigationBar, and a FloatingActionButtonMenu at bottom-end whose action is contextual (Records adds a movement, Loans adds a loan, Home asks). Lists end with a 96px spacer to clear the FAB.
- **Wide:** a WideNavigationRail on surface-container with the FAB in its header; the rail is collapsed below 1200dp and expanded (labels beside icons, extended FAB) at 1200dp and above. Home splits into two equal panes (balance and loans | recent movements) with 24px margins and gutter. Records keeps its filters open as a 340px side pane. Reports flows into two equal columns. Loans becomes list-detail (46% | 54%) at Expanded width only.
- **Readable measure:** settings pages and similar single-column content cap at 720dp and center.
- **Rhythm:** a 4px base with 8, 12, 16, 20, 24 and 32 as the working steps. 16px is the default gap between content blocks and screen margin on compact; 24px separates settings groups and wide panes; 20px is card padding.

## Elevation & Depth

The system is flat and tonal. No component declares a shadow; depth comes from the surface-container stack (low, base, high, highest) and from the single primary slab. The only shadows are the stock Material elevations on FABs, menus and bottom sheets.

**The Tonal Stack Rule.** To raise something, step it one level up the surface-container family; never add a shadow or a border to lift it.

## Shapes

Expressive and soft. The corner scale is extra-small 8, small 12, medium 20, large 28, extra-large 36px: loan cards and side panes use large, the balance panel, amount field, search field and Loans detail pane use extra-large. Chips and buttons are full pills.

Lists are connected groups: rows are separated by Material's segmented gap and share outer corners of the group (24px outer, 6px inner for form groups; Material segmented shapes for list items), so a group reads as one object. A lone row keeps the large outer radius instead of collapsing to small corners.

Category badges are MaterialShapes polygons (Cookie9Sided, Clover4Leaf, Sunny, Cookie6Sided, Gem, Flower, SoftBurst, Pentagon, Puffy, Arch), chosen stably per category. Empty states sit inside a 112px Cookie9Sided polygon on secondary-container.

**The Shape Is Identity Rule.** A category keeps one silhouette and one tone (primary-, tertiary-, secondary-container or surface-container-highest) everywhere: lists, pickers, chart legends, loan cards. Do not reassign shapes per screen or use them decoratively for things that are not categories (empty states and onboarding excepted).

## Components

### Balance Panel (signature)
The heaviest object in the app. A primary slab with extra-large corners and 24px padding: a title-medium "Total Balance" caption at 86% on-primary, the balance in Money Hero, then the month's net (signed) and a 14px two-segment proportion bar (income-container | expense-container, 4px gap, outer ends 7px round, inner ends 3px). Below it, two flow tiles: a 10px color dot, a trend icon and label, and a signed Money Large amount in on-primary. The bar and share animate on the slow spatial spring.

### Category Badge (signature)
A 44px polygon (40px in charts) clipped from the category's MaterialShapes silhouette, filled with its tone, icon at 48% size. While pressed or selected it morphs to a circle on the fast spatial spring. Uncategorized movements fall back to a circle in the money container color with a trend icon.

### Lists and Rows
- **Transaction row:** a SegmentedListItem on surface-container; category badge leading; title one line; supporting line "Category · date" plus 14px icons for recurring, reminder and scheduled; signed Money Medium amount trailing in jade or cochineal.
- **Settings row:** SegmentedListItem on surface-container with a 40px tonal circle icon (secondary-container), chevron for navigation, switch for toggles; the whole row toggles and the row tint never changes with state.
- **Form rows:** 64px minimum rows on surface-container-high using the 24/6 group shape.

### Cards
- **Loan card:** surface-container, large corners, 20px padding, 14px internal rhythm. Badge, title and status chip; remaining amount in Money Large; a LinearWavyProgressIndicator colored by status (primary active, jade paid, error overdue) on a surface-container-highest track, whose wave flattens when paid; next due date (error when overdue) and "n/m paid". Selected cards turn secondary-container.

### Chips
- **Status chip:** a 28px pill with a 16px icon and label-large text. Active is marigold, Paid is jade container, Overdue is error container. Icon plus label always, never color alone.
- **Info chip:** neutral pill on surface-container-highest for a category or loan direction.

### Inputs / Fields
- **Amount field:** the amount is the point of the form. Surface-container-high, extra-large corners, typed at Money Hero size and centered, currency symbol in Money Large before it; the ink animates from on-surface-variant (empty, "0.00") to the direction's money color. Error adds a 2px error border and error label.
- **Global search:** one search for the whole app, opened from the search action in every tab's top bar. A full screen with a 56px pill TextField on surface-container-high (no indicator line), All / Movements / Loans scope chips, and results grouped into Loans and Movements. Matching ignores case and accents and also covers category names and amounts. List screens keep their filters behind a top-bar Tune toggle with a badge counting the active filters.
- **Choice rows:** connected ToggleButton groups (the Expressive replacement for segmented buttons); unselected on surface-container-highest. The income/expense choice in sheets takes the money container colors.
- **Category picker:** a horizontal row of category badges; the chosen one morphs to a circle and gets a 2px primary ring.

### Buttons
- **Sheet primary:** full-width 56px filled button with Expressive morphing shapes and title-medium text.
- **Destructive:** delete in sheets is a filled tonal icon button on error-container; confirm-delete dialogs use a filled error button.
- **FAB:** ToggleFloatingActionButton opening a FloatingActionButtonMenu (add movement, add loan); in the rail it is a FAB or extended FAB with a dropdown menu.

### Navigation and Chrome
ShortNavigationBar (compact) and WideNavigationRail (wide) with filled icons on the active destination and outlined icons otherwise. Screen tops use flexible app bars: medium flexible on Home, large flexible on settings pages, collapsing on scroll. Section headers are title-large with a text-button action at 48px height. Loading uses the Expressive LoadingIndicator.

### Charts
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
- **Do** branch layout on `BoxTheme.widthClass`: panes and the rail when wide, the short bar and FAB menu when compact.
- **Do** pair status color with an icon and a label.

### Don't:
- **Don't** use `error` or red for ordinary spending; expenses use the cochineal expense role.
- **Don't** add shadows or borders to lift a surface; step up the surface-container stack instead.
- **Don't** fill a second surface with primary on the same screen as the balance panel.
- **Don't** tell categories apart by hue alone in charts; use the shape badge and name.
- **Don't** hard-code hex colors in screens or view models; all color lives in the theme.
- **Don't** set money in Roboto body or title styles, or let amounts clip at large font scales; auto-size them.
