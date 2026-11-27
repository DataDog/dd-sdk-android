/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.configuration.ViewEventWriteConfig
import com.datadog.android.rum.integration.tests.assertj.RumEventAssert
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.tests.ktx.getString
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
internal class RumSessionRenewalAlwaysFullViewTest {

    @AutoClose
    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    private lateinit var fakeViewKey: String

    @StringForgery
    private lateinit var fakeViewName: String

    @StringForgery
    private lateinit var fakeActionName: String

    @LongForgery(min = 1L, max = 60_000L)
    private var fakeExtraTimeMs: Long = 0L

    private val startedSessions = mutableListOf<StartedSession>()

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
    }

    // region Inactivity timeout

    @Test
    fun `M start a new session with the active view carried over W addAction() {after inactivity timeout}`() {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        val oldView = lastViewEvent(oldSessionId)
        RumEventAssert.assertThat(oldView)
            .hasViewIsActive(false)
        RumEventAssert.assertThat(singleEvent(type = "action", sessionId = newSessionId))
            .hasActionName(fakeActionName)
            .hasSessionPrecondition("inactivity_timeout")
        val carriedView = lastViewEvent(newSessionId)
        RumEventAssert.assertThat(carriedView)
            .hasViewIsActive(true)
            .hasSessionPrecondition("inactivity_timeout")
        assertThat(viewId(carriedView)).isNotEqualTo(viewId(oldView))
    }

    @Test
    fun `M keep global attributes on the ending and carried over views W addAction() {after inactivity timeout}`(
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeAttributeKey: String,
        @StringForgery fakeAttributeValue: String
    ) {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.addAttribute(fakeAttributeKey, fakeAttributeValue)
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        RumEventAssert.assertThat(lastViewEvent(oldSessionId))
            .hasViewIsActive(false)
            .hasContextAttribute(fakeAttributeKey, fakeAttributeValue)
        RumEventAssert.assertThat(lastViewEvent(newSessionId))
            .hasContextAttribute(fakeAttributeKey, fakeAttributeValue)
    }

    @Test
    fun `M stop the carried over view W stopView() {after inactivity timeout renewal}`() {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        val oldSessionEventCount = eventsInSession(oldSessionId).size

        // When
        rumMonitor.stopView(fakeViewKey)

        // Then
        trackedSessionIds(expectedCount = 2)
        RumEventAssert.assertThat(lastViewEvent(newSessionId))
            .hasViewIsActive(false)
        assertThat(eventsInSession(oldSessionId)).hasSize(oldSessionEventCount)
    }

    @Test
    fun `M start a new session each time W addAction() {after two inactivity timeouts}`() {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val sessionIds = trackedSessionIds(expectedCount = 3)
        var currentSessionId: String? = null
        rumMonitor.getCurrentSessionId { currentSessionId = it }
        assertThat(currentSessionId).isEqualTo(sessionIds.last())
        RumEventAssert.assertThat(lastViewEvent(sessionIds[1]))
            .hasViewIsActive(false)
        RumEventAssert.assertThat(lastViewEvent(sessionIds[2]))
            .hasViewIsActive(true)
    }

    @Disabled("RUM-19867: the final view update of an expired session is written with a no-op writer")
    @Test
    fun `M write the final update of the active view W addAction() {session expired by a non-interaction event}`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)
        // In production, the periodic session expiry check also expires the session this way. It isn't
        // triggered here, as it is posted on the main looper.
        rumMonitor.addError(fakeErrorMessage, RumErrorSource.SOURCE, null)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, _) = trackedSessionIds(expectedCount = 2)
        RumEventAssert.assertThat(lastViewEvent(oldSessionId))
            .hasViewIsActive(false)
    }

    @Disabled("RUM-19867: the resources of the ending session are dropped when the session is renewed")
    @Test
    fun `M write the resource in the ending session W stopResource() {resource started before inactivity timeout}`(
        @StringForgery fakeResourceKey: String,
        @StringForgery(regex = "https://[a-z]+\\.com/[a-z]+") fakeResourceUrl: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeResourceSize: Long
    ) {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        rumMonitor.startResource(fakeResourceKey, RumResourceMethod.GET, fakeResourceUrl)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // When
        rumMonitor.stopResource(fakeResourceKey, HTTP_OK, fakeResourceSize, RumResourceKind.NATIVE)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        val oldView = lastViewEvent(oldSessionId)
        RumEventAssert.assertThat(oldView)
            .hasViewIsActive(false)
            .hasResourceCount(1)
        assertThat(eventsOfType("resource")).hasSize(1)
        RumEventAssert.assertThat(singleEvent(type = "resource", sessionId = oldSessionId))
            .hasResourceUrl(fakeResourceUrl)
            .hasViewId(viewId(oldView))
        RumEventAssert.assertThat(singleEvent(type = "action", sessionId = newSessionId))
            .hasActionName(fakeActionName)
    }

    // endregion

    // region Max duration

    @Test
    fun `M start a new session with the active view carried over W addAction() {after max duration}`() {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        sendActionsUntilMaxDuration(rumMonitor)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        assertThat(eventsOfType("action").map { it.getString("session.id") })
            .isEqualTo(List(ACTIONS_UNTIL_MAX_DURATION) { oldSessionId } + newSessionId)
        RumEventAssert.assertThat(eventsOfType("action").last())
            .hasActionName(fakeActionName)
            .hasSessionPrecondition("max_duration")
        val oldView = lastViewEvent(oldSessionId)
        RumEventAssert.assertThat(oldView)
            .hasViewIsActive(false)
        val carriedView = lastViewEvent(newSessionId)
        RumEventAssert.assertThat(carriedView)
            .hasViewIsActive(true)
            .hasSessionPrecondition("max_duration")
        assertThat(viewId(carriedView)).isNotEqualTo(viewId(oldView))
    }

    @Test
    fun `M attach the error to the carried over view W addError() {after max duration}`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        sendActionsUntilMaxDuration(rumMonitor)

        // When
        rumMonitor.addError(fakeErrorMessage, RumErrorSource.SOURCE, null)

        // Then
        val (_, newSessionId) = trackedSessionIds(expectedCount = 2)
        assertThat(eventsOfType("error")).hasSize(1)
        RumEventAssert.assertThat(singleEvent(type = "error", sessionId = newSessionId))
            .hasErrorMessage(fakeErrorMessage)
            .hasSessionPrecondition("max_duration")
            .hasViewId(viewId(lastViewEvent(newSessionId)))
    }

    // endregion

    // region Background launch

    @Test
    fun `M start a background launch session W addError() {background tracking, after inactivity timeout}`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val rumMonitor = enableRum { trackBackgroundEvents(true) }
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)

        // When
        rumMonitor.addError(fakeErrorMessage, RumErrorSource.SOURCE, null)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        RumEventAssert.assertThat(lastViewEvent(oldSessionId))
            .hasViewIsActive(false)
        assertThat(eventsOfType("error")).hasSize(1)
        RumEventAssert.assertThat(singleEvent(type = "error", sessionId = newSessionId))
            .hasErrorMessage(fakeErrorMessage)
            .hasSessionPrecondition("background_launch")
            .hasViewId(viewId(lastViewEvent(newSessionId)))
    }

    // endregion

    // region Sampling

    @Test
    fun `M discard the renewed session W addAction() {session sample rate 0, after inactivity timeout}`() {
        // Given
        val rumMonitor = enableRum { setSessionSampleRate(0f) }
        rumMonitor.startView(fakeViewKey, fakeViewName)
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)

        // When
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        assertThat(startedSessions).hasSize(2)
        assertThat(startedSessions.map { it.isDiscarded }).containsOnly(true)
        assertThat(startedSessions.map { it.id }).doesNotHaveDuplicates()
        assertThat(rumEvents()).isEmpty()
    }

    // endregion

    // region Stop session

    @Test
    fun `M start a new session with the last view restarted W stopSession() + addAction()`() {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)

        // When
        rumMonitor.stopSession()
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        val oldView = lastViewEvent(oldSessionId)
        RumEventAssert.assertThat(oldView)
            .hasViewIsActive(false)
        RumEventAssert.assertThat(singleEvent(type = "action", sessionId = newSessionId))
            .hasActionName(fakeActionName)
        val restartedView = lastViewEvent(newSessionId)
        RumEventAssert.assertThat(restartedView)
            .hasViewIsActive(true)
        assertThat(viewId(restartedView)).isNotEqualTo(viewId(oldView))
    }

    @Test
    fun `M start a single new session W stopSession() + addAction() {pending resource, after inactivity timeout}`(
        @StringForgery fakeResourceKey: String,
        @StringForgery(regex = "https://[a-z]+\\.com/[a-z]+") fakeResourceUrl: String
    ) {
        // Given
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        rumMonitor.startResource(fakeResourceKey, RumResourceMethod.GET, fakeResourceUrl)

        // When
        rumMonitor.stopSession()
        stubSdkCore.advanceTimeBy(INACTIVITY_TIMEOUT_MS + fakeExtraTimeMs)
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (_, newSessionId) = trackedSessionIds(expectedCount = 2)
        RumEventAssert.assertThat(singleEvent(type = "action", sessionId = newSessionId))
            .hasActionName(fakeActionName)
    }

    @Test
    fun `M record the action only in the new session W stopSession() + addAction() {background tracking}`(
        @StringForgery fakeResourceKey: String,
        @StringForgery(regex = "https://[a-z]+\\.com/[a-z]+") fakeResourceUrl: String
    ) {
        // Given
        val rumMonitor = enableRum { trackBackgroundEvents(true) }
        rumMonitor.startView(fakeViewKey, fakeViewName)
        rumMonitor.startResource(fakeResourceKey, RumResourceMethod.GET, fakeResourceUrl)

        // When
        rumMonitor.stopSession()
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        assertThat(eventsOfType("action")).hasSize(1)
        RumEventAssert.assertThat(singleEvent(type = "action", sessionId = newSessionId))
            .hasActionName(fakeActionName)
        assertThat(eventsInSession(oldSessionId).map { it.getString("view.url") }).doesNotContain(BACKGROUND_VIEW_URL)
    }

    @Test
    fun `M notify Session Replay of the new session only W stopSession() + addAction() + addError()`(
        @StringForgery fakeResourceKey: String,
        @StringForgery(regex = "https://[a-z]+\\.com/[a-z]+") fakeResourceUrl: String,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
        val rumMonitor = enableRum()
        rumMonitor.startView(fakeViewKey, fakeViewName)
        rumMonitor.startResource(fakeResourceKey, RumResourceMethod.GET, fakeResourceUrl)

        // When
        rumMonitor.stopSession()
        rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)
        rumMonitor.addError(fakeErrorMessage, RumErrorSource.SOURCE, null)

        // Then
        val (oldSessionId, newSessionId) = trackedSessionIds(expectedCount = 2)
        val notifiedSessionIds = stubSdkCore.eventsReceived(Feature.SESSION_REPLAY_FEATURE_NAME)
            .mapNotNull { (it as? Map<*, *>)?.get(SESSION_REPLAY_SESSION_ID_KEY) }
        assertThat(notifiedSessionIds).contains(oldSessionId)
        assertThat(notifiedSessionIds.dropWhile { it != newSessionId }).containsOnly(newSessionId)
    }

    // endregion

    @OptIn(ExperimentalRumApi::class)
    private fun enableRum(configure: RumConfiguration.Builder.() -> Unit = {}): RumMonitor {
        val configuration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .trackBackgroundEvents(false)
            .setViewEventWriteConfig(ViewEventWriteConfig.AlwaysFullView)
            .setSessionListener(object : RumSessionListener {
                override fun onSessionStarted(sessionId: String, isDiscarded: Boolean) {
                    startedSessions.add(StartedSession(sessionId, isDiscarded))
                }
            })
            .apply(configure)
            .build()
        Rum.enable(configuration, stubSdkCore)
        return GlobalRumMonitor.get(stubSdkCore)
    }

    private fun sendActionsUntilMaxDuration(rumMonitor: RumMonitor) {
        repeat(ACTIONS_UNTIL_MAX_DURATION) {
            stubSdkCore.advanceTimeBy(ACTION_INTERVAL_MS)
            rumMonitor.addAction(RumActionType.CUSTOM, fakeActionName)
        }
        stubSdkCore.advanceTimeBy(ACTION_INTERVAL_MS + fakeExtraTimeMs)
    }

    // Checks that the listener and the written events report the same tracked sessions, and returns their ids
    private fun trackedSessionIds(expectedCount: Int): List<String> {
        val sessionIds = startedSessions.map { it.id }
        assertThat(startedSessions.map { it.isDiscarded }).containsOnly(false)
        assertThat(sessionIds).hasSize(expectedCount).doesNotHaveDuplicates()
        assertThat(rumEvents().mapNotNull { it.getString("session.id") }.distinct()).isEqualTo(sessionIds)
        return sessionIds
    }

    private fun rumEvents(): List<JsonObject> {
        return stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
            .map { JsonParser.parseString(it.eventData).asJsonObject }
    }

    private fun eventsOfType(type: String): List<JsonObject> {
        return rumEvents().filter { it.getString("type") == type }
    }

    private fun eventsInSession(sessionId: String): List<JsonObject> {
        return rumEvents().filter { it.getString("session.id") == sessionId }
    }

    private fun singleEvent(type: String, sessionId: String): JsonObject {
        val events = eventsOfType(type).filter { it.getString("session.id") == sessionId }
        assertThat(events).hasSize(1)
        return events.single()
    }

    private fun lastViewEvent(sessionId: String): JsonObject {
        val viewEvents = eventsOfType("view")
            .filter { it.getString("session.id") == sessionId && it.getString("view.url") == fakeViewKey }
        return checkNotNull(viewEvents.lastOrNull()) {
            "Expected a view event for $fakeViewKey in session $sessionId"
        }
    }

    private fun viewId(viewEvent: JsonObject): String {
        return checkNotNull(viewEvent.getString("view.id")) { "Expected a view id in $viewEvent" }
    }

    private data class StartedSession(val id: String, val isDiscarded: Boolean)

    companion object {
        // Mirror the defaults of RumSessionScope, which aren't visible from this module
        private val INACTIVITY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15)
        private const val ACTIONS_UNTIL_MAX_DURATION = 23

        // Below the inactivity timeout, and ACTIONS_UNTIL_MAX_DURATION + 1 intervals reach the 4 hours max duration
        private val ACTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)

        private const val HTTP_OK = 200

        // Mirror internal constants of the RUM module, which aren't visible from this module
        private const val SESSION_REPLAY_SESSION_ID_KEY = "sessionId"
        private const val BACKGROUND_VIEW_URL = "com/datadog/background/view"

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
