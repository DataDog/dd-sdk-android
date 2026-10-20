/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.graphics.Bitmap
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImageButtonsActivity
import com.datadog.android.sdk.integration.sessionreplay.SessionReplaySegmentUtils.extractSrSegmentsAsJson
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay._SessionReplayInternalProxy
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream

@RunWith(Parameterized::class)
internal class CompositionEmbeddedContentTest(private val fakeCompositionEnabled: Boolean) :
    BaseSessionReplayTest<SessionReplayImageButtonsActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayImageButtonsActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW),
        compositionEnabled = fakeCompositionEnabled
    )

    @Test
    fun persistEmbeddedRecordsAndResourcesAcrossNavigationAndRecordingRestart() {
        val fakeInitialViewId = awaitSegment { true }.getAsJsonObject("view").get("id").asString
        val fakeTimestamp = System.currentTimeMillis() - 60_000
        val fakeResource = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }.toByteArray()
        lateinit var fakeRoot: LinearLayout
        lateinit var fakeSlot: View
        lateinit var fakeLabel: TextView
        onMain {
            val activity = rule.activity
            fakeLabel = TextView(activity).apply { text = "embedded-host" }
            fakeSlot = View(activity)
            fakeRoot = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(fakeLabel)
                addView(fakeSlot, 200, 200)
            }
            activity.setContentView(fakeRoot)
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(fakeSlot, SLOT_ID)
            // These arrive before the slot's first draw and must wait for its native placeholder.
            _SessionReplayInternalProxy.addEmbeddedContentRecords(
                listOf(record(fakeTimestamp, 4001), record(fakeTimestamp + 17, 4002)),
                SLOT_ID,
                fakeInitialViewId
            )
        }
        _SessionReplayInternalProxy.addEmbeddedContentResource("embedded-parity-resource", fakeResource, "image/png")

        ConditionWatcher {
            val records = segments().filter {
                it.getAsJsonObject("view").get("id").asString == fakeInitialViewId
            }.flatMap { it.getAsJsonArray("records").map { record -> record.asJsonObject } }
                .sortedBy { it.get("timestamp").asLong }
            val embedded = records.filter { it.get("slotId")?.asString == SLOT_ID }
            val placeholder = records.firstOrNull { hasPlaceholder(it) }
            assertThat(placeholder).isNotNull()
            assertThat(embedded).hasSize(2)
            assertThat(embedded.first().get("timestamp").asLong)
                .isGreaterThan(checkNotNull(placeholder).get("timestamp").asLong)
            assertThat(embedded[1].get("timestamp").asLong - embedded[0].get("timestamp").asLong).isEqualTo(17)
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
        awaitResource("embedded-parity-resource", fakeResource)

        // Temporary removal keeps the slot registration alive, as with a recycled/offscreen host.
        onMain { fakeRoot.removeView(fakeSlot) }
        SessionReplay.stopRecording()
        onMain { GlobalRumMonitor.get().startView("embedded-stopped", "embedded-stopped") }
        awaitRecording(false)
        _SessionReplayInternalProxy.addEmbeddedContentRecords(
            listOf(record(fakeTimestamp, 4999)),
            SLOT_ID,
            fakeInitialViewId
        )
        SessionReplay.startRecording()
        onMain { GlobalRumMonitor.get().startView("embedded-next-view", "embedded-next-view") }
        awaitRecording(true)
        val fakeNextViewId = (Datadog.getInstance() as FeatureSdkCore)
            .getFeatureContext(Feature.RUM_FEATURE_NAME)["view_id"] as String
        val fakeNextView = awaitSegment {
            it.getAsJsonObject("view").get("id").asString == fakeNextViewId &&
                it.getAsJsonArray("records").any { record -> record.asJsonObject.get("type").asInt == 10 }
        }
        assertThat(fakeNextView.getAsJsonObject("view").get("id").asString).isNotEqualTo(fakeInitialViewId)
        _SessionReplayInternalProxy.addEmbeddedContentRecords(
            listOf(record(fakeTimestamp, 4003)),
            SLOT_ID,
            fakeNextViewId
        )
        onMain { fakeRoot.addView(fakeSlot, 200, 200) }
        awaitSegment {
            it.getAsJsonObject("view").get("id").asString == fakeNextViewId &&
                it.getAsJsonArray("records").any { record -> record.asJsonObject.get("slotId")?.asString == SLOT_ID }
        }
        assertThat(segments().toString()).doesNotContain("\"id\":4999")

        onMain {
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(fakeSlot, null)
            fakeRoot.removeView(fakeSlot)
            fakeLabel.text = "embedded-detached"
            GlobalRumMonitor.get().startView("embedded-detached", "embedded-detached")
        }
        val fakeDetachedSegment = awaitSegment { segment ->
            segment.getAsJsonArray("records").any {
                it.asJsonObject.get("type").asInt == 10 &&
                    it.asJsonObject.toString().contains("embedded-detached")
            }
        }
        assertThat(fakeDetachedSegment.getAsJsonArray("records").any { hasPlaceholder(it.asJsonObject) }).isFalse()
    }

    private fun record(fakeTimestamp: Long, fakeId: Long): Map<String, Any?> = mapOf(
        "type" to 10,
        "timestamp" to fakeTimestamp,
        "data" to mapOf(
            "wireframes" to listOf(
                mapOf("id" to fakeId, "type" to "shape", "x" to 0, "y" to 0, "width" to 20, "height" to 20)
            )
        )
    )

    private fun hasPlaceholder(fakeRecord: JsonObject): Boolean {
        val data = fakeRecord.getAsJsonObject("data") ?: return false
        val wireframes = data.getAsJsonArray("wireframes")?.map { it.asJsonObject }.orEmpty() +
            data.getAsJsonArray("adds")?.mapNotNull { it.asJsonObject.getAsJsonObject("wireframe") }.orEmpty()
        return wireframes.any {
            it.get("type")?.asString == "embedded_content" && it.get("slotId")?.asString == SLOT_ID
        }
    }

    private fun segments(): List<JsonObject> = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
        .flatMap { it.extractSrSegmentsAsJson() }
        .map { it.asJsonObject }

    private fun awaitSegment(fakePredicate: (JsonObject) -> Boolean): JsonObject {
        var fakeSegment: JsonObject? = null
        ConditionWatcher {
            fakeSegment = segments().lastOrNull(fakePredicate)
            assertThat(
                fakeSegment
            ).describedAs("Uploaded Session Replay segment matching the current phase").isNotNull()
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
        return checkNotNull(fakeSegment)
    }

    private fun awaitResource(fakeId: String, fakeBytes: ByteArray) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            assertThat(
                requests.any {
                    it.textBody?.contains("filename=\"$fakeId\"") == true &&
                        it.requestBuffer.indexOf(fakeBytes.toByteString()) >= 0
                }
            ).describedAs("Embedded resource bytes should reach the local intake").isTrue()
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun awaitRecording(fakeEnabled: Boolean) {
        val sdkCore = Datadog.getInstance() as FeatureSdkCore
        ConditionWatcher {
            assertThat(
                sdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)["session_replay_is_enabled"]
            ).isEqualTo(fakeEnabled)
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    companion object {
        private const val SLOT_ID = "embedded-parity-slot"

        @JvmStatic
        @Parameterized.Parameters(name = "composition={0}")
        fun pipelines(): List<Boolean> = listOf(false, true)
    }
}
