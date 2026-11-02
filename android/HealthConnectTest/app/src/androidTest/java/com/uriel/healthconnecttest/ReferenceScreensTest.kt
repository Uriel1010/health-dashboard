package com.uriel.healthconnecttest

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uriel.healthconnecttest.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Uses an emulator-only fixture through adb reverse; no real account or health data. */
@RunWith(AndroidJUnit4::class)
class ReferenceScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    @Before fun requireIsolatedFixture() {
        assumeTrue("Reference tests must never change a physical phone's settings.", android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("Explicitly opt in with -e referenceFixture true.", InstrumentationRegistry.getArguments().getString("referenceFixture") == "true")
        val connection = URL("http://127.0.0.1:18016/fixture/info").openConnection().apply { connectTimeout = 3000; readTimeout = 3000 }
        assumeTrue("Start scripts/reference_fixture_server.py before running these tests.", connection.getInputStream().bufferedReader().use { it.readText() }.contains("health-companion-reference-fixture"))
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val directory = if (context.resources.configuration.fontScale >= 1.4f) "reference-qa-large" else "reference-qa"
        val destination = File(context.getExternalFilesDir(null), "$directory/$name.png")
        destination.parentFile!!.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap -> destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
    }
    private inline fun Bitmap.useBitmap(action: (Bitmap) -> Unit) { try { action(this) } finally { recycle() } }
    private fun waitFor(text: String) = compose.waitUntil(20000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) { compose.onNodeWithContentDescription(label).performClick(); compose.waitForIdle() }
    private fun scrollTop() {
        compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10000f) }
        compose.waitForIdle()
    }
    @Test fun referenceScreensLightAndDark() {
        runBlocking {
            ServerSettingsRepository(context).saveConnectionSettings("http://127.0.0.1:18016", "emulator-fixture", "Synthetic emulator", "synthetic-ingest",
                "health_session=synthetic-emulator-session", true)
        }
        for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
            runBlocking { AppearancePreferences.setMode(context, mode) }
            tab("Today"); waitFor("1,742"); scrollTop(); screenshot("Today-${mode.name}")
            tab("Food"); waitFor("Meals (3)"); screenshot("Food-${mode.name}")
            compose.onNodeWithText("Register meal manually").performScrollTo().performClick()
            waitFor("Meal name *")
            compose.onNode(hasSetTextAction() and hasText("Meal name *")).performTextReplacement("Chicken Bowl")
            compose.onNode(hasSetTextAction() and hasText("Calories")).performTextReplacement("620")
            compose.onNode(hasSetTextAction() and hasText("Protein")).performTextReplacement("42")
            compose.onNode(hasSetTextAction() and hasText("Carbohydrates")).performTextReplacement("64")
            compose.onNode(hasSetTextAction() and hasText("Fat")).performTextReplacement("18")
            screenshot("Registration-${mode.name}")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Options for Chicken Bowl").performClick()
            compose.onNodeWithText("Delete meal").performClick()
            waitFor("Delete meal?"); screenshot("Delete-${mode.name}")
            compose.onNodeWithText("Cancel").performClick()
            tab("Trends"); waitFor("Daily averages"); screenshot("Trends-${mode.name}")
            compose.onNodeWithContentDescription("Next average period").performClick(); waitFor("Last 14 days")
            compose.onNodeWithContentDescription("Next average period").performClick(); waitFor("Last 30 days")
            compose.onNodeWithText("Activity", useUnmergedTree = true).performClick(); waitFor("Daily activity")
            compose.onNodeWithText("Weight", useUnmergedTree = true).performClick(); waitFor("Weight history")
            tab("Health"); waitFor("Today (from Health Connect)"); screenshot("Health-${mode.name}")
            tab("Settings"); waitFor("Account & server"); screenshot("Settings-${mode.name}")
            compose.onNodeWithText("App updates").performClick(); screenshot("Updates-${mode.name}")
            tab("Today"); compose.onNodeWithText("Synchronization details").performScrollTo().performClick(); screenshot("Sync-${mode.name}")
        }
    }

    @Test fun refreshFailureHasRetryAndNoInventedNutritionValues() {
        fun fail(value: Boolean) {
            val connection = URL("http://127.0.0.1:18016/fixture/fail").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.outputStream.use { it.write("{\"fail\":$value}".toByteArray()) }
                check(connection.responseCode == 200)
            } finally { connection.disconnect() }
        }
        fail(true)
        try {
            runBlocking {
                ServerSettingsRepository(context).saveConnectionSettings("http://127.0.0.1:18016", "emulator-fixture", "Synthetic emulator", "synthetic-ingest",
                    "health_session=synthetic-error-session", true)
            }
            tab("Today")
            waitFor("Nutrition data unavailable")
            waitFor("Could not refresh nutrition data")
            for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
                runBlocking { AppearancePreferences.setMode(context, mode) }
                scrollTop(); screenshot("Error-${mode.name}")
                compose.onNodeWithText("Retry").assertExists()
            }
        } finally { fail(false) }
    }

    @Test fun registrationAndDeletionStillWorkThroughTheNewScreens() {
        runBlocking {
            ServerSettingsRepository(context).saveConnectionSettings("http://127.0.0.1:18016", "emulator-fixture", "Synthetic emulator", "synthetic-ingest",
                "health_session=synthetic-workflow-session", true)
        }
        tab("Food"); waitFor("Meals (3)")
        compose.onNodeWithText("Register meal manually").performScrollTo().performClick()
        waitFor("Meal name *")
        compose.onNode(hasSetTextAction() and hasText("Meal name *")).performTextReplacement("UI regression meal")
        listOf("Calories" to "100", "Protein" to "10", "Carbohydrates" to "12", "Fat" to "2").forEach { (label, value) ->
            compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(value)
        }
        compose.onNode(hasText("Register meal") and hasClickAction()).performClick()
        waitFor("Meals (4)")
        compose.onNodeWithContentDescription("Options for UI regression meal").performScrollTo().performClick()
        compose.onNodeWithText("Delete meal").performClick()
        compose.onNodeWithText("Delete").performClick()
        waitFor("Meals (3)")
    }
}
