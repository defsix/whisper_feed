# UPSTREAM_NOTES.md — Milestone 0: Launcher Feasibility Spike

Status: **build verified in CI-equivalent environment; live on-device Lawnchair
swipe/restart/kill-recovery tests NOT yet performed (no physical device or
emulator available in this environment).** Everything below that is not
explicitly marked "on-device" is verified by building the real upstream
source and reading the real Lawnchair source — not assumption.

## 1. What was forked and built

- Upstream: `NeoApplications/Neo-Feed`, commit `42e023c2f454229055bca35bebdbdbd18ad5f0e3` (shallow clone, `main`).
- Built unmodified with: JDK 21 (Temurin), Gradle wrapper 9.7.1, AGP 9.3.2, Kotlin 2.4.10.
- Android SDK provisioned: `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools` (upstream CI uses JDK 17 on `ubuntu-latest`; JDK 21 also builds cleanly).
- Command: `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (76 tasks, ~6 min cold).
- Output: `app/build/outputs/apk/debug/Neo_Feed_1.9.0_debug.apk` (24.8 MB).
- `aapt dump badging` confirms: `package: name='com.saulhdev.neofeed.dev' versionCode='1900' versionName='1.9.0'`, `minSdk=26`, `targetSdk=37`.

**Important nuance:** the **debug** build variant appends `applicationIdSuffix = ".dev"`, so it installs as `com.saulhdev.neofeed.dev`, not `com.saulhdev.neofeed`. This matters for provider discovery — see §3.

No APK was installed on a device (none available here). The APK above is ready to sideload for the on-device tests in §5.

## 2. The provider mechanism (exact classes/manifest)

`app/src/main/AndroidManifest.xml` declares:

```xml
<service
    android:name=".manager.service.OverlayService"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="com.android.launcher3.WINDOW_OVERLAY" />
        <action android:name="com.google.android.apps.gsa.sidekick.SidekickService" />
        <data android:scheme="app" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.android.launcher3.WINDOW_OVERLAY" />
        <data android:scheme="app" />
    </intent-filter>
    <meta-data android:name="service.api.version" android:value="7" />
</service>
```

`OverlayService` (`com.saulhdev.feeder.manager.service.OverlayService`) is a plain
`Service` that delegates `onBind`/`onUnbind` to a
`ConfigurationOverlayController`, which implements
`com.google.android.libraries.gsa.d.a.OverlaysController` — an AIDL-based
reimplementation of Google's private launcher-overlay protocol (the same one
Google Now Launcher / Pixel Launcher's Discover integration uses). This AIDL
layer lives in the separate `:google-gsa` Gradle module and is credited in
the README to `DrawerOverlayService` by FabianTerhorst, itself a
reverse-engineering of `com.google.android.libraries.launcherclient` (the
matching client-side AIDL is literally still named that in Lawnchair's own
source — see §3).

Other manifest components: `MainActivity` (LAUNCHER category + deep-link
intent filters for `neofeed.saulhdev.com` and a `nf-mastodon://callback`
scheme used for Mastodon OAuth — irrelevant to us but present), and the
`NeoApp` Application class. No content providers, no other services besides
the standard WorkManager `SystemForegroundService`.

## 3. How Lawnchair actually decides who gets to be the feed (read from `LawnchairLauncher/lawnchair`, file `lawnchair/src/app/lawnchair/FeedBridge.kt`)

This is the load-bearing finding for the whole package-rename question, so
it's worth being precise. Lawnchair does **not** just scan for any app that
answers `WINDOW_OVERLAY` — it filters that list through a signature
whitelist:

```kotlin
private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"

fun initializeWhitelist(context: Context) {
    whitelist["com.saulhdev.neofeed"] = getSignatureHash(context, "com.saulhdev.neofeed")  // computed live from whatever's installed
    whitelist["ua.itaysonlab.homefeeder"] = 0x887456ed
    whitelist["launcher.libre.dev"] = 0x2e9dbab5
    whitelist[SmartspacerConstants.SMARTSPACER_PACKAGE_NAME] = 0x15c6e36f
    whitelist["amirz.aidlbridge"] = 0xb662cc2f
    whitelist["com.google.android.googlequicksearchbox"] = 0xe3ca78d8
    whitelist["com.google.android.apps.nexuslauncher"] = 0xb662cc2f
}

fun getAvailableProviders(context: Context) = context.packageManager
    .queryIntentServices(Intent(OVERLAY_ACTION).setData(Uri.parse("app://${context.packageName}")), PackageManager.GET_META_DATA)
    .map { it.serviceInfo.applicationInfo }
    .distinct()
    .filter { getInstance(context).CustomBridgeInfo(it.packageName).isSigned() }
```

`CustomBridgeInfo.isSigned()`:

```kotlin
override val signatureHash = whitelist[packageName]?.toInt() ?: -1
val ignoreWhitelist = prefs.ignoreFeedWhitelist.get()
override fun isSigned(): Boolean {
    ...
    return ignoreWhitelist || signatureHash != -1 && super.isSigned()
}
```

So a package not in `whitelist` gets `signatureHash == -1` and is rejected
**unless** the user-facing preference `pref_ignoreFeedWhitelist` is `true`.
That preference is a real toggle, not a hidden build flag — it's exposed in
Lawnchair's own **Debug Menu** (Settings → About → tap to reveal → "Show
debug menu" → "Ignore feed whitelist"), reachable on any normal installed
Lawnchair build, no root/custom build/Shizuku required. There's a matching
`pref_feedProvider` string preference (Home screen settings → Feed provider)
where the user picks which discovered package to use.

### Direct answer to Milestone 0's mandatory question ("does a package rename break detection?")

**Yes, by default** — `io.zero76.feed` is not in the hardcoded whitelist, so
it will not appear in Lawnchair's Feed Provider picker and will not be
auto-selected.

**No, if the user does one manual one-time setup step** — enable
Lawnchair's "Ignore feed whitelist" debug toggle, then manually pick the
renamed package in Home screen → Feed provider. At that point
`CustomBridgeInfo.isSigned()` returns `true` unconditionally and the
launcher binds to it exactly as it does today for Neo Feed. This requires
**no root, no LSPosed, no Shizuku, and no change to Lawnchair itself** — it
matches the handoff doc's "no root" requirement.

Three longer-term options if we don't want to depend on a manual toggle:
1. Keep relying on the toggle indefinitely (fine for a personal/enthusiast install, which is the stated primary use case).
2. Upstream a PR to `LawnchairLauncher/lawnchair` adding `io.zero76.feed`'s signature hash to the hardcoded `whitelist` map — puts us at the mercy of Lawnchair's release cadence and review, and only helps users of a Lawnchair build that includes it.
3. Ship under the literal package `com.saulhdev.neofeed` — rejected; that's Neo Feed's real identity, not ours, and would be actively misleading.

Recommendation: proceed with `io.zero76.feed` + the toggle (option 1). It's
consistent with "primary launcher target: Lawnchair" for a single
enthusiast device, not a mass-market whitelist submission.

### Also relevant: the debug-suffix trap

Because upstream's debug build type sets `applicationIdSuffix = ".dev"`,
even *today's* unmodified Neo Feed, built as `assembleDebug`, installs as
`com.saulhdev.neofeed.dev` — which is **also not** in the hardcoded
whitelist (only the exact string `com.saulhdev.neofeed` is). So on-device
testing of the unmodified upstream app must either (a) build `assembleRelease`
with a signing config so the applicationId is the bare
`com.saulhdev.neofeed`, or (b) use the same "ignore whitelist" + manual
picker path described above even before any renaming happens. Worth keeping
in mind so a "why doesn't the debug APK show up as a feed provider" moment
isn't mistaken for a deeper problem.

## 4. Licensing (action needed before any code import)

Neo Feed is **GPLv3+**, copyright © 2025 Saul Henriquez & Antonios Hazim.
Its README credits iTaysonLab's `HomeFeeder` as the project it was forked
from, and `FabianTerhorst/DrawerOverlayService` as the base for the overlay
service.

**This repository currently has an MIT `LICENSE` file.** MIT and GPLv3 are
not compatible in the direction we'd need (a GPLv3+ derivative work cannot
be redistributed under a plain MIT license). Before any Neo Feed–derived
source is actually copied into this repository, the repo's license needs to
become GPLv3+ (or GPLv3-compatible with clear per-file/module attribution),
and an attribution/`LICENSES` area needs to preserve the upstream copyright
notices. I haven't changed the LICENSE file yet — flagging this for your
decision before Milestone 1 starts copying code in, since it's a real
licensing commitment, not just a formality.

## 5. On-device tests still required (not possible in this remote environment)

This environment has no Android emulator, no physical device, and no adb
target — so the following from the handoff's Milestone 0 checklist are
still open and need to be run on your Pixel 10 Pro:

- [ ] Sideload the built debug APK (or a release build) and confirm Lawnchair's Feed Provider picker shows it (expect: it won't, until the whitelist toggle + manual pick from §3 is done).
- [ ] With the toggle enabled and Neo Feed picked as feed provider: confirm swipe-right-from-Home opens it.
- [ ] Confirm the feed survives a Lawnchair restart.
- [ ] Force-stop the Neo Feed process and confirm Lawnchair recovers (re-binds) on next swipe.
- [ ] Repeat the above after renaming `applicationId` to `io.zero76.feed` (Milestone 1 territory, but the whitelist behavior should be re-confirmed once renamed).

I can produce a signed/aligned release APK and exact adb sideload commands whenever you're ready to run these.

## 6. Conclusion / recommendation

The launcher-integration mechanism is fully understood from source on both
sides (Neo Feed's service + Lawnchair's discovery code), the unmodified
upstream builds cleanly against current tooling, and the package-rename
question has a concrete, low-risk answer (manual toggle, no root). This
satisfies everything in Milestone 0 that can be verified without a physical
device. Recommend: proceed to the on-device checklist in §5 when you have
the Pixel available, and separately decide on the LICENSE question in §4
before Milestone 1 begins importing/adapting Neo Feed source.
