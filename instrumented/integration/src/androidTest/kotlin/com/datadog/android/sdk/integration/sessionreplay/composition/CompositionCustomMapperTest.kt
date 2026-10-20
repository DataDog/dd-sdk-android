/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.composition

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.InternalLogger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.datadog.android.sdk.integration.sessionreplay.INITIAL_WAIT_MS
import com.datadog.android.sdk.integration.sessionreplay.SessionReplayImageButtonsActivity
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.SR_PRIVACY_LEVEL
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.setSessionReplayTextAndInputPrivacy
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class CompositionCustomMapperTest(fakeCompositionEnabled: Boolean) :
    BaseSessionReplayTest<SessionReplayImageButtonsActivity>() {

    @get:Rule
    override val rule = SessionReplayTestRule(
        SessionReplayImageButtonsActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW),
        extensions = listOf(CustomExtension()),
        compositionEnabled = fakeCompositionEnabled
    )

    @Test
    fun persistAsynchronousCustomMappingWithSelectorPrivacy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            activity.setContentView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(CustomTextView(activity).apply { text = "custom-allowed" })
                    addView(
                        CustomSelector(activity).apply {
                            setSessionReplayTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL_INPUTS)
                            addView(CustomTextView(activity).apply { text = "custom-private" })
                        }
                    )
                }
            )
            GlobalRumMonitor.get().startView("custom-parity", "custom-parity")
        }
        runInstrumentationScenario()

        ConditionWatcher {
            val fakeRecords = extractRecordsFromRequests(rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl))
            val fakePayload = fakeRecords.toString()
            assertThat(fakePayload).contains("mapped:custom-allowed", "mapped:***").doesNotContain("custom-private")
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private class CustomTextView(context: Context) : AppCompatTextView(context)
    private class CustomSelector(context: Context) : LinearLayout(context)

    private class CustomExtension : ExtensionSupport {
        override fun name(): String = "parity-test"
        override fun getCustomDrawableMapper(): List<DrawableToColorMapper> = emptyList()
        override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> = listOf(
            object : OptionSelectorDetector {
                override fun isOptionSelector(view: ViewGroup): Boolean = view is CustomSelector
            }
        )

        override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> = listOf(
            MapperTypeWrapper(
                CustomTextView::class.java,
                object : WireframeMapper<CustomTextView> {
                    override fun map(
                        view: CustomTextView,
                        mappingContext: MappingContext,
                        asyncJobStatusCallback: AsyncJobStatusCallback,
                        internalLogger: InternalLogger
                    ): List<MobileSegment.Wireframe> {
                        val bounds = DefaultViewBoundsResolver.resolveViewGlobalBounds(
                            view,
                            mappingContext.systemInformation.screenDensity
                        )
                        val id = DefaultViewIdentifierResolver.resolveViewId(view)
                        val text = if (
                            mappingContext.hasOptionSelectorParent &&
                            mappingContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
                        ) {
                            "***"
                        } else {
                            view.text.toString()
                        }
                        val wireframes = mutableListOf<MobileSegment.Wireframe>()
                        asyncJobStatusCallback.jobStarted()
                        // Completes after map() returns, without retaining the View or blocking the UI.
                        Handler(Looper.getMainLooper()).post {
                            wireframes += MobileSegment.Wireframe.TextWireframe(
                                id = id,
                                x = bounds.x,
                                y = bounds.y,
                                width = bounds.width,
                                height = bounds.height,
                                text = "mapped:$text",
                                textStyle = MobileSegment.TextStyle("monospace", 12, "#000000ff")
                            )
                            asyncJobStatusCallback.jobFinished()
                        }
                        return wireframes
                    }
                }
            )
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "composition={0}")
        fun pipelines(): List<Boolean> = listOf(false, true)
    }
}
