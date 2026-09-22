package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When the app is allowed to fetch on its own.
 *
 * Two independent switches, Wi-Fi only and charging only, rather than one
 * "when to sync" list. The list was the first plan and it was short of an
 * entry: it could not say *unlimited data, but not on battery*. All four
 * combinations of these two are real, which is when switches beat a list —
 * unlike `articleOpenMode`, where two booleans encoded three modes and one
 * combination meant nothing at all.
 *
 * Both halves of this are the kind that go on compiling after being undone,
 * so both are read here.
 */
class SyncConstraintsTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val activity = source("MainActivity.kt")
    private val prefs = source("data/content/FeedPreferences.kt")
    private val page = source("ui/pages/PreferencesPage.kt")

    @Test
    fun `charging is its own preference, off unless asked for`() {
        assertTrue(prefs.contains("var syncOnlyWhenCharging = BooleanPref"))
        val declaration = prefs.substringAfter("var syncOnlyWhenCharging = BooleanPref")
            .substringBefore(")")
        assertTrue(
            "a phone that is rarely plugged in must not stop syncing by default",
            declaration.contains("defaultValue = false"),
        )
        assertTrue(
            "the reader has to be told what it costs",
            declaration.contains("summaryId = R.string.pref_sync_charging_summary"),
        )
    }

    @Test
    fun `the summary names the way round it`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val summary = strings.substringAfter("pref_sync_charging_summary\">").substringBefore("<")
        assertTrue("says the sync waits: $summary", summary.contains("wait"))
        assertTrue(
            "and that pull to refresh still works, which is what keeps a held " +
                "sync from reading as a broken app: $summary",
            summary.contains("Pull to refresh"),
        )
    }

    @Test
    fun `the constraint is applied, and only when chosen`() {
        assertTrue(activity.contains("setRequiresCharging(true)"))
        val guarded = Regex(
            """if \(prefs\.syncOnlyWhenCharging\.getValue\(\)\) \{\s*constraints\.setRequiresCharging\(true\)""",
        )
        assertTrue(
            "an unasked-for charging constraint stops the app syncing at all",
            guarded.containsMatchIn(activity),
        )
        assertTrue(
            "battery-not-low stays: it is the weaker guard, not a replacement",
            activity.contains("setRequiresBatteryNotLow(true)"),
        )
    }

    /**
     * Pull-to-refresh is what makes a held scheduled sync recoverable rather
     * than a dead app, so it must not grow constraints of its own. The
     * scheduled work is the only thing that carries them.
     */
    @Test
    fun `only the scheduled work is constrained`() {
        val syncing = Regex("""setRequiresCharging""").findAll(activity).count()
        assertTrue("one place, inside configurePeriodicSync", syncing == 1)
        val configure = activity.substringAfter("private fun configurePeriodicSync()")
        assertTrue(configure.contains("setRequiresCharging(true)"))
    }

    /**
     * A constraint changed in Settings has to reach WorkManager.
     *
     * The schedule was configured once in onCreate and never again, so both
     * switches only took effect on the next cold start — turning Wi-Fi-only on
     * and watching the app go on syncing over mobile data was the behaviour.
     */
    @Test
    fun `changing a switch reschedules the work`() {
        val onCreate = activity.substringBefore("private fun configurePeriodicSync()")
        assertTrue(
            "the schedule is still configured once per cold start",
            onCreate.contains("configurePeriodicSync()"),
        )
        assertTrue(
            "and again whenever an input to it changes",
            onCreate.contains("prefs.syncOnlyWhenCharging.get()") &&
                onCreate.contains("prefs.syncOnlyOnWifi.get()") &&
                onCreate.contains("prefs.syncFrequency.get()"),
        )
        assertTrue(
            "re-enqueuing has to keep the existing work rather than duplicate it",
            activity.contains("ExistingPeriodicWorkPolicy.UPDATE"),
        )
    }

    @Test
    fun `it is offered beside the switch it belongs with`() {
        val fetching = page.substringAfter("val fetchingPrefs = listOf(").substringBefore(")")
        assertTrue(fetching.contains("prefs.syncOnlyOnWifi"))
        assertTrue("and in the same group", fetching.contains("prefs.syncOnlyWhenCharging"))
    }
}
