# Budgetide

Offline-first Personal Finance & Money Decision Manager.

## Included
- Kotlin + Jetpack Compose, Room local database (v2 schema), no backend, no API keys
- Dashboard: balance, income/expense, category breakdown ("where did my money go"),
  necessary-vs-unnecessary spending split, financial health score
- Transactions: add/delete, tagged Necessary/Unnecessary
- Goals: add, add-funds, delete
- Calculators: can-I-afford-it, future savings projection, "what if my salary decreases"
  scenario simulator — with an in-app disclaimer that these are educational estimates
- Recurring payments & EMIs screen: add/delete, total monthly outflow + EMI-only outflow
- Warranties tracker: item, expiry date, days remaining
- Lending tracker ("who owes me money"): add, settle/reopen, delete
- Backup & Restore: export all local data to a JSON file anywhere you choose
  (Android's file picker), and restore from one — no server involved
- Adaptive launcher icon wired up (mipmap-anydpi-v26 + colors.xml), using the
  bar-chart vector already in the project as the foreground

## Fixed
- **Goals duplicating on every app open**: sample-data seeding used to check a
  `StateFlow`'s cached `.value`, which is still an empty list right after the
  ViewModel is created (it fills in later, once the UI subscribes to the
  underlying Room `Flow`). That race caused sample goals/transactions/recurring
  rows to be re-inserted on nearly every cold start. Seeding now uses one-shot
  `suspend` row-count queries and one-shot `.first()` reads instead, so it only
  ever runs once, the first time the database is truly empty.

## Before publishing to Play Store
1. **Build-verify**: open in Android Studio, Gradle sync, run on a device/emulator.
   This project was hand-edited without a Kotlin/Android toolchain available in the
   editing environment — review compiler output and report back anything that
   doesn't compile.
2. **Replace the icon** (optional): the adaptive launcher icon currently reuses
   the existing `ic_launcher_foreground.xml` vector. If you have your own logo,
   use Android Studio's Image Asset Studio (right-click `app` → New → Image Asset)
   to regenerate it from your PNG — it'll overwrite the files this pass created.
3. **Privacy policy**: `privacy-policy.html` (shipped alongside this project,
   not inside the app) is a ready-to-host draft. Fill in the `[DATE]` and
   contact email placeholders, host it (e.g. GitHub Pages), and paste that URL
   into the Play Console listing — required even for fully offline apps.
4. **Data safety form** in Play Console: since Budgetide collects nothing and
   has no network calls, you should be able to answer "no data collected."
5. **Keep your release keystore safe** — you can't update the app on Play
   Store later without signing with the same key.
6. **Store listing assets**: screenshots, feature graphic, short/long description.

## Notes
- DB version bumped 1 → 2 for the new tables/columns (`warranties`, `lending`,
  `essential` on transactions, `category` on recurring). Uses
  `fallbackToDestructiveMigration()`, so any device that ran the old version
  will have its local database wiped and reseeded the first time this build
  runs. Fine pre-release; replace with a real `Migration` before you have real
  user data you can't afford to lose.
- Calculator results are simplified educational estimates, not regulated
  financial advice (disclaimed in-app and in the privacy policy).

## UI polish (this pass)
- Splash screen (androidx.core `core-splashscreen`) using the existing launcher vector
- Onboarding: 3-page intro shown once on first launch (skippable), persisted via DataStore
  so it never shows again after that
- Empty states with icon + friendly copy on every list screen (transactions, goals,
  recurring, warranties, lending) instead of bare text
- "Where did my money go" is now an animated donut chart with a color-coded legend,
  instead of stacked progress bars
- Goal progress bars animate smoothly instead of jumping instantly
- Dark mode: status bar / navigation bar now switch with the system theme
  (previously hardcoded light, mismatched the in-app dark colors)
- Icons on the "More" hub cards (Recurring, Warranties, Lending) with matching accent colors

## Bug fixes & internationalization (this pass)
- **Format string bug**: a couple of sentences (the dashboard's Future Savings
  example and the salary-decrease scenario) had `%.0f`/`%.1f` placeholders
  that were never actually passed through `.format()`, so they showed up as
  literal text instead of numbers. Fixed by formatting each value into its
  own string first, then interpolating.
- **Hardcoded ₹ symbol removed**: `money()` now uses
  `NumberFormat.getCurrencyInstance(Locale.getDefault())`, so the app shows
  $, €, £, ¥, etc. automatically based on the user's own device locale/region,
  instead of always showing Indian Rupees regardless of who's using it.
- **Export/Restore backup buttons**: were left-aligned instead of centered.
  Fixed with `Modifier.fillMaxWidth()` on the row, `weight(1f)` on each
  button, and `TextAlign.Center` on the button text.
- **Colorful, tinted cards**: added `tintedContainer()` in Theme.kt, which
  blends a semantic color into the current surface color for a soft, on-brand
  card background. Applied across dashboard, calculators, and Pro screens
  instead of every card being the same flat surface color.

## Budgetide Pro (one-time purchase)
Added a free/Pro split using Google Play Billing (one-time, non-consumable
product, no subscription):

**Free**: transactions, dashboard basics, can-I-afford-it & future-savings
calculators, 6 built-in categories (Food, Rent, Travel, Bills, Entertainment,
Other).

**Pro** (unlocks everything else): advanced reports (category donut chart,
necessary-vs-unnecessary analysis), financial goals, recurring payments &
EMIs, warranty tracking, lending tracker, "what if my salary decreases"
scenario, custom categories, backup & restore. "Family accounts" is listed
as coming soon (not implemented) and "no ads" doesn't currently apply since
the app has no ad SDK integrated either way.

**Required setup before this works, on your end:**
1. In Play Console → your app → Monetize → Products → In-app products,
   create a one-time product with the exact ID `budgetide_pro_upgrade`
   (or change `PRO_PRODUCT_ID` in `billing/BillingManager.kt` to match
   whatever ID you use).
2. Set its price there (e.g. ₹299) — Play Console controls the real price,
   not the app code.
3. Billing only works on a build installed through Play Store's internal
   testing track (or with a license tester account) — a debug build
   side-loaded via USB cannot complete a real purchase.
4. The `com.android.vending.BILLING` permission is bundled inside the
   `billing-ktx` library's own manifest and merges in automatically — no
   manual manifest edit needed for that.

## Dates, monthly comparison, and dev testing (this pass)
- Every transaction now has a real, editable date (date picker in the
  add/edit dialog, defaulting to today, capped at today so you can't log a
  future transaction).
- Transactions screen now groups entries by day like Google Pay/most payment
  apps: "Today", "Yesterday", then the full date, each showing that day's
  transactions with a timestamp under the category.
- New "This month vs last month" comparison card (Pro feature, consistent
  with the other advanced-report gating) showing both totals and the %
  change, colour-coded by whether spending went up or down.
- Added a **Developer options** toggle in the More screen - "Simulate Pro" -
  visible only in debug builds (`BuildConfig.DEBUG`), so you can preview
  every Pro-gated screen while developing without needing a real Play
  Billing purchase. It never appears in release/Play Store builds and never
  affects the real entitlement check there.

## Recurring/EMI date + Pro-gating reliability (this pass)
- Adding a recurring payment/EMI now has a real date picker ("Deduction
  date" for EMI, "Next due date" otherwise) instead of always silently
  defaulting to 30 days from today.
- `isPro` now uses `SharingStarted.Eagerly` instead of `WhileSubscribed`, so
  entitlement state is always live from the moment the ViewModel is created,
  not only once some screen happens to be observing it. This closes a
  possible stale-value race right after toggling "Simulate Pro" or
  completing a purchase - the same class of bug fixed earlier for the goals
  duplication issue.
- The "You're on Budgetide Pro" screen now explicitly lists every unlocked
  feature (instead of just a bare success message), and hides the
  redundant Free/Premium comparison once you're already Pro. If Pro is
  active via the debug-only Simulate Pro switch, that's called out inline
  so it's never confused with a real purchase.

## Cleanup, editing, and branding pass (this version)
- **Dashboard bug fixed**: removed the "Future Savings" card that was
  showing fixed hardcoded numbers (₹10,000/5 years/8%) no matter what you
  entered anywhere else in the app. The real, working future-savings
  calculator already lives on the Calculators screen with editable fields -
  the dashboard version was a leftover duplicate and the actual bug.
- **Edit added to Goals, Recurring & EMIs, Warranties, and Lending** -
  each row now has an edit (pencil) icon alongside delete, opening the same
  add dialog pre-filled, matching how Transactions already worked. Warranty
  editing now uses a direct expiry-date picker instead of "months from
  today", which didn't make sense to re-edit relative to the current date.
- **App icon fixed**: the adaptive icon's background layer was still
  Android Studio's default placeholder grid pattern from before the real
  logo was set - only the foreground (your actual "B" logo) had been
  replaced. Background now uses the app's brand teal instead.
- **Richer card colors**: increased the default tint blend app-wide (16% ->
  24%, plus bumped several individual overrides) and added drop-shadow
  elevation to every card that didn't have any - Material3's plain `Card`
  has zero shadow by default, which was reading flat/basic.
- **Full internal branding cleanup**: renamed the last few internal-only
  identifiers that still said "moneywise" (never shown in the UI): the
  default backup export filename, the onboarding preferences file, the
  Room database filename, and the in-app product ID. See the two callouts
  below - these two are not purely cosmetic.

### Important: two renames with real consequences
- **Database filename** (`budgetide.db`, was `moneywise.db`): if this
  update is installed over a version that already has real saved data,
  the app will start fresh under the new filename - the old data isn't
  deleted, just no longer reachable from the app. Use Export backup (Pro)
  first if you want to carry existing data forward, then Restore after
  updating.
- **In-app product ID** (`budgetide_pro_upgrade`, was
  `moneywise_pro_upgrade`): if you already created the one-time product in
  Play Console under the old ID, purchases will fail until you either
  create a new product with the new ID, or revert `PRO_PRODUCT_ID` in
  `BillingManager.kt` back to the old value to match what's already live
  in Play Console.

## Cross-device currency and splash icon fix (this pass)
- **Currency fixed to always show ₹**: `money()` was using
  `Locale.getDefault()`, which reads the *device's own* region setting -
  a phone set to a US/UK/etc region showed $/£ instead of ₹. Now hardcoded
  to `Locale("en", "IN")` so every user sees Rupees regardless of their
  phone's locale.
- **Splash screen icon fixed for older Android versions**: the splash icon
  was left unset, relying on it falling back to the app's launcher icon.
  That fallback only works on Android 12+ (API 31+) native SplashScreen -
  on API 26-30 devices, the compat splashscreen library does NOT auto-derive
  the icon that way, and silently shows a generic default (the stock
  Android robot) instead. This is why it looked correct on a dev device/
  emulator (likely API 31+) but broke on another phone. Now set explicitly
  to the real logo (`@mipmap/ic_launcher_foreground`) in both light and
  dark styles, so it's consistent on every supported API level.
- Re-applied the adaptive launcher icon background fix (brand teal instead
  of the Android Studio default grid pattern) to this project copy, since
  it wasn't present here yet.
