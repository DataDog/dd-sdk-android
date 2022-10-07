/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.event.EventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.activities.ViewTrackingActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.TestEncryption
import com.datadog.android.nightly.utils.aResourceKey
import com.datadog.android.nightly.utils.aResourceMethod
import com.datadog.android.nightly.utils.aViewKey
import com.datadog.android.nightly.utils.aViewName
import com.datadog.android.nightly.utils.anActionName
import com.datadog.android.nightly.utils.anErrorMessage
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.executeInsideView
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.invokeMethod
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class RumConfigE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    // region Enable/Disable Feature

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_rum_feature_enabled() {
        val testMethodName = "rum_config_rum_feature_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder().build()
            )
        }
        sendAllRumEvents(forge, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setBatchSize(BatchSize): Builder
     */
    @Test
    fun rum_config_custom_batch_size() {
        val testMethodName = "rum_config_custom_batch_size"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder()
                    .setBatchSize(forge.aValueFrom(BatchSize::class.java)).build()
            )
        }
        sendAllRumEvents(forge, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_rum_feature_disabled() {
        val testMethodName = "rum_config_rum_feature_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = false,
                    crashReportsEnabled = true
                ).build()
            )
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    // endregion

    // region ViewEventMapper

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumViewEventMapper(com.datadog.android.event.ViewEventMapper): Builder
     */
    @Test
    fun rum_config_set_rum_view_event_mapper() {
        val testMethodName = "rum_config_set_rum_view_event_mapper"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumViewEventMapper(
                    eventMapper = object : ViewEventMapper {
                        override fun map(event: ViewEvent): ViewEvent {
                            event.view.name =
                                forge.aStringMatching("$MAPPED_NAME_PREFIX[a-zA-z]{3,10}")
                            return event
                        }
                    }
                ).build()
            )
        }

        val key = forge.aViewKey()
        val name = forge.aViewName()
        GlobalRum.get().startView(
            key,
            name,
            defaultTestAttributes(testMethodName)
        )
        GlobalRum.get().stopView(key, defaultTestAttributes(testMethodName))
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumViewEventMapper(com.datadog.android.event.ViewEventMapper): Builder
     */
    @Test
    fun rum_config_set_rum_view_event_mapper_map_to_copy() {
        val testMethodName = "rum_config_set_rum_view_event_mapper_map_to_copy"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumViewEventMapper(
                    eventMapper = object : ViewEventMapper {
                        override fun map(event: ViewEvent): ViewEvent {
                            return event.copy()
                        }
                    }
                ).build()
            )
        }

        val key = forge.aViewKey()
        val name = forge.aViewName()
        GlobalRum.get().startView(
            key,
            name,
            defaultTestAttributes(testMethodName)
        )
        GlobalRum.get().stopView(key, defaultTestAttributes(testMethodName))
    }

    // endregion

    // region ResourceEventMapper

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumResourceEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ResourceEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_resource_event_mapper_map_to_null() {
        val testMethodName = "rum_config_set_rum_resource_event_mapper_map_to_null"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumResourceEventMapper(
                    eventMapper = object : EventMapper<ResourceEvent> {
                        override fun map(event: ResourceEvent): ResourceEvent? {
                            return null
                        }
                    }
                ).build()
            )
        }

        sendRandomResource(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumResourceEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ResourceEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_resource_event_mapper_map_to_copy() {
        val testMethodName = "rum_config_set_rum_resource_event_mapper_map_to_copy"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumResourceEventMapper(
                    eventMapper = object : EventMapper<ResourceEvent> {
                        override fun map(event: ResourceEvent): ResourceEvent {
                            return event.copy()
                        }
                    }
                ).build()
            )
        }

        sendRandomResource(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumResourceEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ResourceEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_resource_event_mapper() {
        val testMethodName = "rum_config_set_rum_resource_event_mapper"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumResourceEventMapper(
                    eventMapper = object : EventMapper<ResourceEvent> {
                        override fun map(event: ResourceEvent): ResourceEvent {
                            event.resource.url = forge.aResourceKey(MAPPED_URL_PREFIX)
                            return event
                        }
                    }
                ).build()
            )
        }

        sendRandomResource(testMethodName)
    }

    // endregion

    // region ActionEventMapper

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumActionEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ActionEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_action_event_mapper_map_to_null() {
        val testMethodName = "rum_config_set_rum_action_event_mapper_map_to_null"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true,
                    sessionReplayEnabled = true
                ).setRumActionEventMapper(
                    eventMapper = object : EventMapper<ActionEvent> {
                        override fun map(event: ActionEvent): ActionEvent? {
                            return null
                        }
                    }
                ).build()
            )
        }

        sendRandomActionEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumActionEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ActionEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_action_event_mapper_map_to_copy() {
        val testMethodName = "rum_config_set_rum_action_event_mapper_map_to_copy"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumActionEventMapper(
                    eventMapper = object : EventMapper<ActionEvent> {
                        override fun map(event: ActionEvent): ActionEvent {
                            return event.copy()
                        }
                    }
                ).build()
            )
        }

        sendRandomActionEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumActionEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ActionEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_action_event_mapper() {
        val testMethodName = "rum_config_set_rum_action_event_mapper"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumActionEventMapper(
                    eventMapper = object : EventMapper<ActionEvent> {
                        override fun map(event: ActionEvent): ActionEvent {
                            event.view.url = forge.aViewKey(prefix = MAPPED_URL_PREFIX)
                            return event
                        }
                    }
                ).build()
            )
        }

        sendRandomActionEvent(testMethodName)
    }

    // endregion

    // region ErrorEventMapper

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumErrorEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ErrorEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_error_event_mapper_map_to_null() {
        val testMethodName = "rum_config_set_rum_error_event_mapper_map_to_null"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumErrorEventMapper(
                    eventMapper = object : EventMapper<ErrorEvent> {
                        override fun map(event: ErrorEvent): ErrorEvent? {
                            return null
                        }
                    }
                ).build()
            )
        }

        sendRandomErrorEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumErrorEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ErrorEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_error_event_mapper_map_to_copy() {
        val testMethodName = "rum_config_set_rum_error_event_mapper_map_to_copy"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumErrorEventMapper(
                    eventMapper = object : EventMapper<ErrorEvent> {
                        override fun map(event: ErrorEvent): ErrorEvent {
                            return event.copy()
                        }
                    }
                ).build()
            )
        }

        sendRandomErrorEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumErrorEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.ErrorEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_error_event_mapper() {
        val testMethodName = "rum_config_set_rum_error_event_mapper"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setRumErrorEventMapper(
                    eventMapper = object : EventMapper<ErrorEvent> {
                        override fun map(event: ErrorEvent): ErrorEvent {
                            event.view.url = forge.aViewKey(prefix = MAPPED_URL_PREFIX)
                            return event
                        }
                    }
                ).build()
            )
        }

        sendRandomErrorEvent(testMethodName)
    }

    // endregion

    // region LongTaskEventMapper

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackLongTasks(Long = DEFAULT_LONG_TASK_THRESHOLD_MS): Builder
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumLongTaskEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.LongTaskEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_longtask_event_mapper_map_to_null() {
        val testMethodName = "rum_config_set_rum_longtask_event_mapper_map_to_null"
        measureSdkInitialize {
            val builder = defaultConfigurationBuilder(
                logsEnabled = true,
                tracesEnabled = true,
                rumEnabled = true,
                crashReportsEnabled = true
            )
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                builder
                    .setRumLongTaskEventMapper(
                        eventMapper = object : EventMapper<LongTaskEvent> {
                            override fun map(event: LongTaskEvent): LongTaskEvent? {
                                return null
                            }
                        }
                    )
                    .trackLongTasks()
                    .build()
            )
        }

        sendRandomLongTaskEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackLongTasks(Long = DEFAULT_LONG_TASK_THRESHOLD_MS): Builder
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumLongTaskEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.LongTaskEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_longtask_event_mapper_map_to_copy() {
        val testMethodName = "rum_config_set_rum_longtask_event_mapper_map_to_copy"
        measureSdkInitialize {
            val builder = defaultConfigurationBuilder(
                logsEnabled = true,
                tracesEnabled = true,
                rumEnabled = true,
                crashReportsEnabled = true
            )
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                builder
                    .setRumLongTaskEventMapper(
                        eventMapper = object : EventMapper<LongTaskEvent> {
                            override fun map(event: LongTaskEvent): LongTaskEvent {
                                return event.copy()
                            }
                        }
                    )
                    .trackLongTasks()
                    .build()
            )
        }

        sendRandomLongTaskEvent(testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackLongTasks(Long = DEFAULT_LONG_TASK_THRESHOLD_MS): Builder
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setRumLongTaskEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.rum.model.LongTaskEvent>): Builder
     */
    @Test
    fun rum_config_set_rum_longtask_event_mapper() {
        val testMethodName = "rum_config_set_rum_longtask_event_mapper"
        measureSdkInitialize {
            val builder = defaultConfigurationBuilder(
                logsEnabled = true,
                tracesEnabled = true,
                rumEnabled = true,
                crashReportsEnabled = true
            )
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                builder
                    .setRumLongTaskEventMapper(
                        eventMapper = object : EventMapper<LongTaskEvent> {
                            override fun map(event: LongTaskEvent): LongTaskEvent {
                                event.view.url = forge.aViewKey(prefix = MAPPED_URL_PREFIX)
                                return event
                            }
                        }
                    )
                    .trackLongTasks()
                    .build()
            )
        }

        sendRandomLongTaskEvent(testMethodName)
    }

    // endregion

    // region Session Sample

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun sampleRumSessions(Float): Builder
     */
    @Test
    fun rum_config_sample_rum_sessions_all_in() {
        val testMethodName = "rum_config_sample_rum_sessions_all_in"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).sampleRumSessions(100f).build()
            )
        }

        sendAllRumEvents(forge, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun sampleRumSessions(Float): Builder
     */
    @Test
    fun rum_config_sample_rum_sessions_all_out() {
        val testMethodName = "rum_config_sample_rum_sessions_all_out"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).sampleRumSessions(0f).build()
            )
        }

        sendAllRumEvents(forge, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun sampleRumSessions(Float): Builder
     */
    @Test
    fun rum_config_sample_rum_sessions_75_percent_in() {
        val testMethodName = "rum_config_sample_rum_sessions_75_percent_in"
        val eventsNumber = 100
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).sampleRumSessions(75f).build()
            )
        }

        repeat(eventsNumber) {
            // we do not want to add the test method name in the parent View name as
            // this will add extra events to the monitor query value
            sendRandomRumEvent(forge, testMethodName, parentViewEventName = "")
            // expire the session here
            GlobalRum.get().invokeMethod("resetSession")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.SecurityConfig#constructor(com.datadog.android.security.Encryption?)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setSecurityConfig(SecurityConfig): Builder
     * apiMethodSignature: com.datadog.android.security.Encryption#fun encrypt(ByteArray): ByteArray
     * apiMethodSignature: com.datadog.android.security.Encryption#fun decrypt(ByteArray): ByteArray
     */
    @Test
    fun rum_config_set_security_config_with_encryption() {
        val testMethodName = "rum_config_set_security_config_with_encryption"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                Configuration
                    .Builder(
                        logsEnabled = true,
                        tracesEnabled = true,
                        rumEnabled = true,
                        crashReportsEnabled = true,
                        sessionReplayEnabled = true
                    )
                    .setSecurityConfig(SecurityConfig(localDataEncryption = TestEncryption()))
                    .build()

            )
        }
        sendAllRumEvents(forge, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setVitalsUpdateFrequency(VitalsUpdateFrequency): Builder
     */
    @Test
    fun rum_config_set_vitals_update_frequency_never() {
        val testMethodName = "rum_config_set_vitals_update_frequency_never"
        val strategy = ActivityViewTrackingStrategy(
            true,
            componentPredicate = object : ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return true
                }

                override fun getViewName(component: Activity): String {
                    return testMethodName
                }
            }
        )
        // we need to initialize the SDK on the Main Thread (Looper thread) otherwise the
        // Choreographer Callback will not be registered
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            measureSdkInitialize {
                val config = Configuration
                    .Builder(
                        logsEnabled = true,
                        tracesEnabled = true,
                        rumEnabled = true,
                        crashReportsEnabled = true,
                        sessionReplayEnabled = true
                    )
                    .useViewTrackingStrategy(strategy)
                    .setVitalsUpdateFrequency(VitalsUpdateFrequency.NEVER)
                    .build()
                initializeSdk(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    TrackingConsent.GRANTED,
                    config
                )
            }
        }
        val scenario = launch(ViewTrackingActivity::class.java)
        // Give some time to vitals monitors to be updated
        Thread.sleep(5000)
        scenario.onActivity {
            InstrumentationRegistry.getInstrumentation().callActivityOnPause(it)
            InstrumentationRegistry.getInstrumentation().callActivityOnResume(it)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setVitalsUpdateFrequency(VitalsUpdateFrequency): Builder
     */
    @Test
    fun rum_config_set_vitals_update_frequency() {
        val fakeFrequency = forge.aValueFrom(
            VitalsUpdateFrequency::class.java,
            exclude = listOf(VitalsUpdateFrequency.NEVER)
        )
        val testMethodName = "rum_config_set_vitals_update_frequency"
        val strategy = ActivityViewTrackingStrategy(
            true,
            componentPredicate = object : ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return true
                }

                override fun getViewName(component: Activity): String {
                    return testMethodName
                }
            }
        )
        // we need to initialize the SDK on the Main Thread (Looper thread) otherwise the
        // Choreographer Callback will not be registered
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            measureSdkInitialize {
                val config = Configuration
                    .Builder(
                        logsEnabled = true,
                        tracesEnabled = true,
                        rumEnabled = true,
                        crashReportsEnabled = true,
                        sessionReplayEnabled = true
                    )
                    .useViewTrackingStrategy(strategy)
                    .setVitalsUpdateFrequency(fakeFrequency)
                    .build()
                initializeSdk(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    TrackingConsent.GRANTED,
                    config
                )
            }
        }
        val scenario = launch(ViewTrackingActivity::class.java)
        // Give some time to vitals monitors to be updated
        Thread.sleep(5000)
        scenario.onActivity {
            InstrumentationRegistry.getInstrumentation().callActivityOnPause(it)
            InstrumentationRegistry.getInstrumentation().callActivityOnResume(it)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    // endregion

    // region Internal

    private fun sendRandomResource(testMethodName: String) {
        executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
            val resourceKey = forge.aResourceKey()
            GlobalRum.get().startResource(
                resourceKey,
                forge.aResourceMethod(),
                resourceKey,
                defaultTestAttributes(testMethodName)
            )
            GlobalRum.get().stopResource(
                resourceKey,
                forge.anInt(min = 200, max = 500),
                forge.aLong(min = 1),
                forge.aValueFrom(RumResourceKind::class.java),
                defaultTestAttributes(testMethodName)
            )
        }
    }

    private fun sendRandomActionEvent(testMethodName: String) {
        executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
            GlobalRum.get().addUserAction(
                forge.aValueFrom(RumActionType::class.java),
                forge.anActionName(),
                defaultTestAttributes(testMethodName)
            )
            sendRandomActionOutcomeEvent(forge)
            Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        }
    }

    private fun sendRandomErrorEvent(testMethodName: String) {
        executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
            GlobalRum.get().addError(
                forge.anErrorMessage(),
                forge.aValueFrom(RumErrorSource::class.java),
                forge.aNullable { forge.aThrowable() },
                defaultTestAttributes(testMethodName)
            )
        }
    }

    private fun sendRandomLongTaskEvent(testMethodName: String) {
        executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
            GlobalRum.addAttribute(TEST_METHOD_NAME_KEY, testMethodName)
            Handler(Looper.getMainLooper()).post {
                Thread.sleep(100)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    // endregion

    companion object {
        const val MAPPED_NAME_PREFIX = "mappedName"
        const val MAPPED_URL_PREFIX = "mappedUrl"
    }
}
