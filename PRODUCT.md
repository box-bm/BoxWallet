# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Individuals in Central America who manage their own money on their own phone: they log day-to-day expenses and incomes, schedule recurring incomes and payments, and follow personal loans paid in installments. They use the app in Spanish or English, in short sessions (log a purchase right after it happens, check the month's balance, mark an installment as paid). Devices include regular phones and foldables (Pixel Fold class) used both folded and unfolded.

## Product Purpose
BoxWallet makes a personal budget legible: how much came in, how much went out, what is still owed, and where the money goes by category. Success is a user who logs a movement in seconds, always knows the month's balance and outstanding loan installments, and trusts that the numbers are theirs and correct.

## Positioning
Private by construction: no account, no server, all data stays on the device (optional Google Drive backup the user controls). Built for Central American currencies and the loan-in-installments reality common in the region, not a generic US budgeting clone.

## Operating Context
- Quick capture moments: logging a transaction from a bottom sheet, often one-handed.
- Review moments: month summary on Home, trends and category breakdown in Reports (Analytics), installment schedule in Loan detail.
- Maintenance: categories, main currency, theme, biometric lock, reminders/notifications, Excel report export, Drive backup/restore, data deletion, analytics opt-out.
- First run goes through onboarding; biometric lock screen guards the app when enabled.

## Capabilities and Constraints
- Kotlin + Jetpack Compose, Material3 1.5.0-alpha (Expressive APIs available), minSdk 31, targetSdk 36. Hilt, Room, DataStore, WorkManager.
- Destinations: Home, Movements (list of every income and expense), Loans (+ Loan detail), Reports (analytics), Settings (General, Categories, Theme, Backup, Data management), Onboarding, Lock screen.
- Localized: English (default) and Spanish (`values-es`).
- Multi-currency: the main currency is chosen by country in onboarding; each movement can use any ISO currency, with the exchange rate for its date frozen at save time. Rates sync daily from a public API (fawazahmed0/exchange-api) and no user data is sent.
- Must run well folded and unfolded on foldables and on tablets (adaptive layouts confirmed as a requirement).

## Brand Commitments
- Name: BoxWallet.
- The app has its own brand palette as the default look; Material You dynamic color remains an opt-in in Theme settings (confirmed).
- Design language: Material 3 Expressive (confirmed by the owner). Everything else about the current look may change; all features stay.

## Evidence on Hand
- No testimonials, metrics, or press. Store listing guide in `PLAY_STORE_IMAGES.md` (Spanish). Do not invent user counts or claims.

## Product Principles
1. Seconds to log: capture is always one obvious action away.
2. The balance is the truth: the month's numbers lead every overview and never hide behind decoration.
3. Private and calm: local data, no nagging; money states (income, expense, owed, paid) are unambiguous at a glance.
4. One app, every posture: folded, unfolded, and tablet layouts are first-class, not stretched phones.

## Accessibility & Inclusion
Follow Android/Material accessibility: 48dp targets, sp type that honors font scale, color never the only signal for income vs. expense, TalkBack labels on icon-only controls, Remove-animations respected.
