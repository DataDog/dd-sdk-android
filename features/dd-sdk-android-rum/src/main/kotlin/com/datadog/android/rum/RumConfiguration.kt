/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Looper
import androidx.annotation.FloatRange
import com.datadog.android.event.EventMapper
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent

/**
 * Describes configuration to be used for the RUM feature.
 */
data class RumConfiguration internal constructor(
    internal val applicationId: String,
    internal val featureConfiguration: RumFeature.Configuration
) {

    /**
     * A Builder class for a [RumConfiguration].
     *
     * @param applicationId your applicationId for RUM events
     */
    @Suppress("TooManyFunctions")
    class Builder(private val applicationId: String) {

        private var rumConfig = RumFeature.DEFAULT_RUM_CONFIG

        /**
         * Sets the sample rate for RUM Sessions.
         *
         * @param sampleRate the sample rate must be a value between 0 and 100. A value of 0
         * means no RUM event will be sent, 100 means all sessions will be kept.
         */
        fun setSessionSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            rumConfig = rumConfig.copy(sampleRate = sampleRate)
            return this
        }

        /**
         * Whether to collect accessibility attributes - this is disabled by default.
         *
         * @param enabled whether collecting accessibility attributes is enabled or not.
         */
        fun collectAccessibility(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(collectAccessibility = enabled)
            return this
        }

        /**
         * Sets the sample rate for Internal Telemetry (info related to the work of the
         * SDK internals). Default value is 20.
         *
         * @param sampleRate the sample rate must be a value between 0 and 100. A value of 0
         * means no telemetry will be sent, 100 means all telemetry will be kept.
         */
        fun setTelemetrySampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            rumConfig = rumConfig.copy(telemetrySampleRate = sampleRate)
            return this
        }

        /**
         * Enable the user interaction automatic tracker. By enabling this feature the SDK will intercept
         * UI interaction events (e.g.: taps, scrolls, swipes) and automatically send those as RUM UserActions for you.
         * @param touchTargetExtraAttributesProviders an array with your own implementation of the
         * target attributes provider.
         * @param interactionPredicate an interface to provide custom values for the
         * actions events properties.
         * @see [ViewAttributesProvider]
         * @see [InteractionPredicate]
         */
        @JvmOverloads
        fun trackUserInteractions(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider> = emptyArray(),
            interactionPredicate: InteractionPredicate = NoOpInteractionPredicate()
        ): Builder {
            rumConfig = rumConfig.copy(
                touchTargetExtraAttributesProviders = touchTargetExtraAttributesProviders.toList(),
                interactionPredicate = interactionPredicate
            )
            return this
        }

        /**
         * Disable the user interaction automatic tracker.
         */
        fun disableUserInteractionTracking(): Builder {
            rumConfig = rumConfig.copy(userActionTracking = false)
            return this
        }

        /**
         * Sets the automatic view tracking strategy used by the SDK.
         * By default [ActivityViewTrackingStrategy] will be used.
         * @param strategy as the [ViewTrackingStrategy]
         * Note: If [null] is passed, the RUM Monitor will let you handle View events manually.
         * This means that you should call [RumMonitor.startView] and [RumMonitor.stopView]
         * yourself. A view should be started when it becomes visible and interactive
         * (equivalent to `onResume`) and be stopped when it's paused (equivalent to `onPause`).
         * @see [com.datadog.android.rum.tracking.ActivityViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.FragmentViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.MixedViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.NavigationViewTrackingStrategy]
         */
        fun useViewTrackingStrategy(strategy: ViewTrackingStrategy?): Builder {
            rumConfig = rumConfig.copy(viewTrackingStrategy = strategy)
            return this
        }

        /**
         * Enable long operations on the main thread to be tracked automatically.
         * Any long running operation on the main thread will appear as Long Tasks in Datadog
         * RUM Explorer
         * @param longTaskThresholdMs the threshold in milliseconds above which a task running on
         * the Main thread [Looper] is considered as a long task (default 100ms). Setting a
         * value less than or equal to 0 disables the long task tracking
         */
        @JvmOverloads
        fun trackLongTasks(longTaskThresholdMs: Long = RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS): Builder {
            val strategy = if (longTaskThresholdMs > 0) {
                MainLooperLongTaskStrategy(longTaskThresholdMs)
            } else {
                null
            }
            rumConfig = rumConfig.copy(longTaskTrackingStrategy = strategy)
            return this
        }

        /**
         * Enable tracking of non-fatal ANRs. This is enabled by default on Android API 29 and
         * below, and disabled by default on Android API 30 and above. Android API 30+ has a
         * capability to report fatal ANRs (always enabled). Please note, that tracking non-fatal
         * ANRs is using Watchdog thread approach, which can be noisy, and also leads to ANR
         * duplication on Android 30+ if fatal ANR happened, because Watchdog thread approach cannot
         * categorize ANR as fatal or non-fatal.
         *
         * @param enabled whether tracking of non-fatal ANRs is enabled or not.
         */
        fun trackNonFatalAnrs(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(trackNonFatalAnrs = enabled)
            return this
        }

        /**
         * Sets the [ViewEventMapper] for the RUM [ViewEvent]. You can use this interface implementation
         * to modify the [ViewEvent] attributes before serialisation.
         *
         * @param eventMapper the [ViewEventMapper] implementation.
         */
        fun setViewEventMapper(eventMapper: ViewEventMapper): Builder {
            rumConfig = rumConfig.copy(viewEventMapper = eventMapper)
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ResourceEvent]. You can use this interface implementation
         * to modify the [ResourceEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setResourceEventMapper(eventMapper: EventMapper<ResourceEvent>): Builder {
            rumConfig = rumConfig.copy(resourceEventMapper = eventMapper)
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ActionEvent]. You can use this interface implementation
         * to modify the [ActionEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setActionEventMapper(eventMapper: EventMapper<ActionEvent>): Builder {
            rumConfig = rumConfig.copy(actionEventMapper = eventMapper)
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ErrorEvent]. You can use this interface implementation
         * to modify the [ErrorEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setErrorEventMapper(eventMapper: EventMapper<ErrorEvent>): Builder {
            rumConfig = rumConfig.copy(errorEventMapper = eventMapper)
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [LongTaskEvent]. You can use this interface implementation
         * to modify the [LongTaskEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setLongTaskEventMapper(eventMapper: EventMapper<LongTaskEvent>): Builder {
            rumConfig = rumConfig.copy(longTaskEventMapper = eventMapper)
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [VitalEvent]. You can use this interface implementation
         * to modify the [VitalEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setVitalEventMapper(eventMapper: EventMapper<VitalEvent>): Builder {
            rumConfig = rumConfig.copy(vitalEventMapper = eventMapper)
            return this
        }

        /**
         * Enables/Disables tracking RUM event when no Activity is in foreground.
         *
         * By default, background events are not tracked. Enabling this feature might increase the
         * number of sessions tracked and impact your billing.
         *
         * @param enabled whether background events should be tracked in RUM.
         */
        fun trackBackgroundEvents(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(backgroundEventTracking = enabled)
            return this
        }

        /**
         * Enables/Disables tracking of frustration signals.
         *
         * By default frustration signals are tracked. Currently the SDK supports detecting
         * error taps which occur when an error follows a user action tap.
         *
         * @param enabled whether frustration signals should be tracked in RUM.
         */
        fun trackFrustrations(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(trackFrustrations = enabled)
            return this
        }

        /**
         * Allows to specify the frequency at which to update the mobile vitals
         * data provided in the RUM [ViewEvent].
         * @param frequency as [VitalsUpdateFrequency]
         * @see [VitalsUpdateFrequency]
         */
        fun setVitalsUpdateFrequency(frequency: VitalsUpdateFrequency): Builder {
            rumConfig = rumConfig.copy(vitalsMonitorUpdateFrequency = frequency)
            return this
        }

        /**
         * Let the RUM feature target a custom server.
         * The provided url should be the full endpoint url, e.g.: https://example.com/rum/upload
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            rumConfig = rumConfig.copy(customEndpointUrl = endpoint)
            return this
        }

        /**
         * Sets the Session listener.
         * @param sessionListener the listener to notify whenever a new Session starts.
         */
        fun setSessionListener(sessionListener: RumSessionListener): Builder {
            rumConfig = rumConfig.copy(sessionListener = sessionListener)
            return this
        }

        /**
         * Sets a custom identifier for initial network resources used to compute the time to network settled
         * in a RUM View event. By default, the SDK uses a [TimeBasedInitialResourceIdentifier] with
         * a threshold of 100ms.
         * @param initialResourceIdentifier the [InitialResourceIdentifier] to use.
         */
        fun setInitialResourceIdentifier(initialResourceIdentifier: InitialResourceIdentifier): Builder {
            rumConfig = rumConfig.copy(initialResourceIdentifier = initialResourceIdentifier)
            return this
        }

        /**
         * Sets a custom identifier for the last interaction in the previous view used to compute the time from
         * the last interaction to the next view metric.
         * By default, the SDK uses a [TimeBasedInteractionIdentifier] with a threshold of 3000ms.
         * @param lastInteractionIdentifier the [LastInteractionIdentifier] to use. Setting this property to
         * null will disable the Interaction to Next View metric.
         */
        fun setLastInteractionIdentifier(lastInteractionIdentifier: LastInteractionIdentifier?): Builder {
            rumConfig = rumConfig.copy(lastInteractionIdentifier = lastInteractionIdentifier)
            return this
        }

        /**
         * The [SlowFramesListener] provides statistical data to help identify performance issues related to UI rendering:
         *
         * - slowFrames: A list of records containing the timestamp and duration of frames where users experience
         *   jank frames within the given view.
         *
         * - slowFrameRate: The rate of slow frames encountered during the view's lifetime.
         *
         * - freezeRate: The rate of freeze occurrences during the view's lifetime.
         *
         *
         * This configuration sets the parameters for the [SlowFramesListener], which are used to calculate the slow frames array,
         * slow frame ratio, and freeze ratio. For additional details, refer to [SlowFramesConfiguration].
         *
         * Assigning a null value to this property will disable the [SlowFramesListener] and stop the computation of the
         * associated rates.
         *
         * @param slowFramesConfiguration The configuration to be applied to the [SlowFramesListener].
         */
        @ExperimentalRumApi
        fun setSlowFramesConfiguration(
            slowFramesConfiguration: SlowFramesConfiguration?
        ): Builder {
            rumConfig = rumConfig.copy(slowFramesConfiguration = slowFramesConfiguration)
            return this
        }

        /**
         * Sets the [InsightsCollector] to collect RUM Insights events, used inside the RUM Debug Widget.
         *
         * @param insightsCollector the [InsightsCollector] implementation.
         * @return the [Builder] instance.
         */
        @InternalApi
        @ExperimentalRumApi
        fun setInsightsCollector(insightsCollector: InsightsCollector): Builder {
            rumConfig = rumConfig.copy(insightsCollector = insightsCollector)
            return this
        }

        /**
         * Enables/Disables collection of an anonymous user ID across sessions.
         *
         * By default, the SDK generates a unique, non-personal anonymous user ID that is
         * persisted across app launches. This ID is attached to each RUM session, allowing
         * to link sessions originating from the same user/device without collecting personal data.
         */
        fun trackAnonymousUser(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(trackAnonymousUser = enabled)
            return this
        }

        /**
         * Builds a [RumConfiguration] based on the current state of this Builder.
         */
        fun build(): RumConfiguration {
            val telemetryConfigurationSampleRate =
                rumConfig
                    .additionalConfig[RumFeature.DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG]?.let {
                    if (it is Number) it.toFloat() else null
                }
            return RumConfiguration(
                applicationId = applicationId,
                featureConfiguration = rumConfig.let {
                    if (telemetryConfigurationSampleRate != null) {
                        rumConfig.copy(
                            telemetryConfigurationSampleRate = telemetryConfigurationSampleRate
                        )
                    } else {
                        rumConfig
                    }
                }
            )
        }

        // region Internal

        @Suppress("FunctionMaxLength")
        internal fun setTelemetryConfigurationEventMapper(
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): Builder {
            rumConfig = rumConfig.copy(telemetryConfigurationMapper = eventMapper)
            return this
        }

        /**
         * Allows to provide additional configuration values which can be used by the RUM feature.
         * @param additionalConfig Additional configuration values.
         */
        internal fun setAdditionalConfiguration(additionalConfig: Map<String, Any>): Builder {
            rumConfig = rumConfig.copy(additionalConfig = additionalConfig)
            return this
        }

        /**
         * Set custom [ActionTrackingStrategy] RUM actions tracking.
         *
         * @param composeActionTrackingStrategy custom actions tracking strategy.
         */
        internal fun setComposeActionTrackingStrategy(
            composeActionTrackingStrategy: ActionTrackingStrategy
        ): Builder {
            rumConfig =
                rumConfig.copy(composeActionTrackingStrategy = composeActionTrackingStrategy)
            return this
        }

        /**
         * Set RUM session type that will be propagated to all RUM events and used regardless of
         * whether the session is happening inside a synthetic test or not.
         */
        internal fun setRumSessionTypeOverride(rumSessionTypeOverride: RumSessionType): Builder {
            rumConfig = rumConfig.copy(rumSessionTypeOverride = rumSessionTypeOverride)
            return this
        }
    }
}
