/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImageButtonsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import com.datadog.android.sessionreplay.compose.sessionReplayHide
import com.datadog.android.sessionreplay.compose.sessionReplayTextAndInputPrivacy
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.android.sessionreplay.setSessionReplayTextAndInputPrivacy
import com.datadog.tools.unit.ConditionWatcher
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class CompositionPipelineParityTest(private val fakeCompositionEnabled: Boolean) :
    BaseSessionReplayTest<SessionReplayImageButtonsActivity>() {

    override val rule = SessionReplayTestRule(
        SessionReplayImageButtonsActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW),
        extensions = listOf(ComposeExtensionSupport(), MaterialExtensionSupport()),
        compositionEnabled = fakeCompositionEnabled
    )
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(composeRule).around(rule)

    @Test
    fun recordNativeAndMaterialWidgetsWithInheritedPrivacy() {
        lateinit var fakeAllowedSlider: Slider
        lateinit var fakeMaskedSlider: Slider
        onMain {
            val activity = rule.activity
            activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
            val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(TextView(activity).apply { text = "native-parity" })
            root.addView(CheckBox(activity).apply { text = "checked-option"; isChecked = true })
            root.addView(RadioButton(activity).apply { text = "radio-option"; isChecked = true })
            root.addView(SeekBar(activity).apply { progress = 37 }, 600, 100)
            root.addView(NumberPicker(activity).apply { minValue = 0; maxValue = 9; value = 4 }, 200, 250)
            root.addView(Chip(activity).apply { text = "material-chip"; isCheckable = true; isChecked = true })
            fakeAllowedSlider = Slider(activity).apply { valueFrom = 0f; valueTo = 100f; value = 37f }
            root.addView(fakeAllowedSlider, 600, 150)
            fakeMaskedSlider = Slider(activity).apply {
                valueFrom = 0f
                valueTo = 100f
                value = 73f
                setSessionReplayTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL_INPUTS)
            }
            root.addView(fakeMaskedSlider, 600, 150)
            root.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setSessionReplayTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL)
                    addView(TextView(activity).apply { text = "native-private-label" })
                    addView(EditText(activity).apply { setText("native-private-input") })
                }
            )
            activity.setContentView(root)
            GlobalRumMonitor.get().startView("native-parity", "native-parity")
        }
        runInstrumentationScenario()

        val fakeWireframes = awaitWireframes("native-parity")
        assertThat(fakeWireframes.mapNotNull { it.get("text")?.asString })
            .contains("checked-option", "radio-option", "material-chip")
            .doesNotContain("native-private-label", "native-private-input")
        assertThat(shapesWithin(fakeAllowedSlider, fakeWireframes)).hasSize(3)
        assertThat(shapesWithin(fakeMaskedSlider, fakeWireframes)).hasSize(1)
    }

    @Test
    fun refreshComposePrivacyAcrossRecompositionAndNativeInterop() {
        val fakeVersion = mutableStateOf(0)
        val fakePrivacy = mutableStateOf(TextAndInputPrivacy.MASK_ALL)
        val fakeBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        onMain {
            val activity = rule.activity
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        Column(Modifier.padding(16.dp).sessionReplayTextAndInputPrivacy(fakePrivacy.value)) {
                            Text(
                                "compose-public-${fakeVersion.value}",
                                Modifier.sessionReplayTextAndInputPrivacy(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
                            )
                            Text("compose-private-label")
                            BasicTextField("compose-private-input", onValueChange = {})
                            AndroidView(
                                factory = { EditText(it).apply { setText("interop-private-input") } }
                            )
                            BasicTextField(
                                "compose-password",
                                onValueChange = {},
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                            Text("compose-hidden-label", Modifier.sessionReplayHide(true))
                            Image(fakeBitmap.asImageBitmap(), "source-image", Modifier.size(20.dp))
                        }
                    }
                }
            )
            GlobalRumMonitor.get().startView("compose-parity-0", "compose-parity-0")
        }
        composeRule.waitForIdle()
        runInstrumentationScenario()

        val fakeMaskedWireframes = awaitWireframes("compose-public-0")
        assertThat(fakeMaskedWireframes.toString()).doesNotContain(
            "compose-private-label",
            "compose-private-input",
            "compose-password",
            "compose-hidden-label"
        )
        if (fakeCompositionEnabled) {
            // Inherited Compose-to-native privacy is provided by the composition pipeline.
            assertThat(fakeMaskedWireframes.toString()).doesNotContain("interop-private-input")
        }
        assertThat(
            fakeMaskedWireframes.map { it.get("type")?.asString }
        ).contains("image")

        onMain {
            fakeVersion.value = 1
            fakePrivacy.value = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
            GlobalRumMonitor.get().startView("compose-parity-1", "compose-parity-1")
        }
        composeRule.waitForIdle()
        runInstrumentationScenario()

        val fakeAllowedWireframes = awaitWireframes("compose-public-1")
        assertThat(fakeAllowedWireframes.toString())
            .contains("compose-private-label", "compose-private-input", "interop-private-input")
            .doesNotContain("compose-password", "compose-hidden-label")
    }

    @Test
    fun recordComposeDialogsAndPopupsWithLocalPrivacy() {
        onMain {
            val activity = rule.activity
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        Text("host-parity")
                        Dialog(onDismissRequest = {}) {
                            Column(Modifier.padding(16.dp)) {
                                Text("dialog-parity")
                                Text(
                                    "dialog-private",
                                    Modifier.sessionReplayTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL)
                                )
                            }
                        }
                        Popup {
                            Column(Modifier.padding(16.dp)) {
                                Text("popup-parity")
                                Text("popup-hidden", Modifier.sessionReplayHide(true))
                            }
                        }
                    }
                }
            )
            GlobalRumMonitor.get().startView("window-parity", "window-parity")
        }
        composeRule.waitForIdle()
        runInstrumentationScenario()

        val fakeWireframes = awaitWireframes("dialog-parity", "popup-parity")
        assertThat(fakeWireframes.toString()).doesNotContain("dialog-private", "popup-hidden")
    }

    private fun awaitWireframes(vararg fakeExpectedTexts: String): List<JsonObject> {
        var fakeWireframes = emptyList<JsonObject>()
        ConditionWatcher {
            val records = extractRecordsFromRequests(rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl))
                .sortedBy { it.get("timestamp").asLong }
            fakeWireframes = replayWireframes(records)
            assertThat(fakeWireframes.mapNotNull { it.get("text")?.asString })
                .describedAs("Current replay text")
                .containsAll(fakeExpectedTexts.toList())
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
        return fakeWireframes
    }

    /** Apply wireframe mutations so late window discovery and recomposition are checked as replayed. */
    private fun replayWireframes(fakeRecords: List<JsonObject>): List<JsonObject> {
        val fakeWireframes = linkedMapOf<Long, JsonObject>()
        fakeRecords.forEach { record ->
            val data = record.getAsJsonObject("data") ?: return@forEach
            when (record.get("type").asInt) {
                10 -> {
                    fakeWireframes.clear()
                    data.getAsJsonArray("wireframes").forEach {
                        val wireframe = it.asJsonObject
                        fakeWireframes[wireframe.get("id").asLong] = wireframe.deepCopy()
                    }
                }
                11 -> if (data.get("source")?.asInt == 0) {
                    data.getAsJsonArray("removes")?.forEach { fakeWireframes.remove(it.asJsonObject.get("id").asLong) }
                    data.getAsJsonArray("adds")?.forEach {
                        val wireframe = it.asJsonObject.getAsJsonObject("wireframe")
                        fakeWireframes[wireframe.get("id").asLong] = wireframe.deepCopy()
                    }
                    data.getAsJsonArray("updates")?.forEach { update ->
                        val fields = update.asJsonObject
                        fakeWireframes[fields.get("id").asLong]?.let { wireframe ->
                            fields.entrySet().forEach { (key, value) -> wireframe.add(key, value.deepCopy()) }
                        }
                    }
                }
            }
        }
        return fakeWireframes.values.toList()
    }

    private fun shapesWithin(fakeView: View, fakeWireframes: List<JsonObject>): List<JsonObject> {
        val fakeBounds = Rect()
        onMain {
            val position = IntArray(2)
            fakeView.getLocationOnScreen(position)
            val density = fakeView.resources.displayMetrics.density
            fakeBounds.set(
                (position[0] / density).toInt(),
                (position[1] / density).toInt(),
                ((position[0] + fakeView.width) / density).toInt(),
                ((position[1] + fakeView.height) / density).toInt()
            )
        }
        return fakeWireframes.filter {
            it.get("type")?.asString == "shape" &&
                fakeBounds.contains(it.get("x").asInt, it.get("y").asInt)
        }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "composition={0}")
        fun pipelines(): List<Boolean> = listOf(false, true)
    }
}
