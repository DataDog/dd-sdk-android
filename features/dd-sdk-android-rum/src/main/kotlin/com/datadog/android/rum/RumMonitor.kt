/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.FloatRange
import androidx.fragment.app.Fragment
import com.datadog.android.Datadog
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.telemetry.internal.TelemetryEventHandler
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.tools.annotation.NoOpImplementation

/**
 *  A class enabling Datadog RUM features.
 *
 *  It allows you to record User events that can be explored and analyzed in Datadog Dashboards.
 *
 *  You can only have one active RumMonitor, and should register/retrieve it from the [GlobalRumMonitor] object.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
@NoOpImplementation
interface RumMonitor {

    /**
     * Notifies that a View is being shown to the user, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * [Activity] or [Fragment] instance).
     * @param name the name of the view (usually your [Activity] or [Fragment] full class name)
     * @param attributes additional custom attributes to attach to the view. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [stopView]
     */
    fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started View, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * [Activity] or [Fragment] instance).
     * @param attributes additional custom attributes to attach to the view. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startView]
     */
    fun stopView(
        key: Any,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an action happened.
     * This is used to track discrete actions (e.g.: tap).
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startAction]
     * @see [stopAction]
     */
    fun addAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that an action started.
     * This is used to track long running actions (e.g.: scroll). Such an action must
     * be stopped with [stopAction], and will be stopped automatically if it lasts more than
     * 10 seconds.
     * @param type the action type
     * @param name the action identifier
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [stopAction]
     * @see [addAction]
     */
    fun startAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that an action stopped, and update the action's type and name.
     * This is used to stop tracking long running actions (e.g.: scroll), started
     * with [startAction].
     * @param type the action type (overriding the last started action)
     * @param name the action identifier (overriding the last started action)
     * @param attributes additional custom attributes to attach to the action. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [addAction]
     * @see [startAction]
     */
    fun stopAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notify that a new Resource is being loaded, linked with the [key] instance.
     * @param key the instance that represents the resource being loaded (usually your
     * request or network call instance).
     * @param method the method used to load the resource (E.g., for network: "GET" or "POST")
     * @param url the url or local path of the resource being loaded
     * @param attributes additional custom attributes to attach to the resource. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [stopResource]
     * @see [stopResourceWithError]
     */
    fun startResource(
        key: String,
        method: String,
        url: String,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param size the size of the resource, in bytes
     * @param kind the type of resource loaded
     * @param attributes additional custom attributes to attach to the resource. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startResource]
     * @see [stopResourceWithError]
     */
    fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startResource]
     * @see [stopResource]
     */
    fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Stops a previously started Resource that failed loading, linked with the [key] instance by
     * providing the intercepted stacktrace.
     * Note: This method should only be used from hybrid application.
     * @param key the instance that represents the active view (usually your
     * request or network call instance).
     * @param statusCode the status code of the resource (if any)
     * @param message a message explaining the error
     * @param source the source of the error
     * @param stackTrace the error stacktrace
     * @param errorType the type of the error. Usually it should be the canonical name of the
     * of the Exception class.
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     * @see [startResource]
     * @see [stopResource]
     */
    @SuppressWarnings("LongParameterList")
    fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        stackTrace: String,
        errorType: String?,
        attributes: Map<String, Any?> = emptyMap()
    )

    /**
     * Notifies that an error occurred in the active View.
     * @param message a message explaining the error
     * @param source the source of the error
     * @param throwable the throwable
     * @param attributes additional custom attributes to attach to the error. Attributes can be
     * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
     */
    fun addError(
        message: String,
        source: RumErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    )

    /**
     * Notifies that an error occurred in the active View.
     *
     * This method is meant for non-native or cross platform frameworks (such as React Native or
     * Flutter) to send error information to Datadog. Although it can be used directly, it is
     * recommended to pass a Throwable instead.
     *
     * @param message a message explaining the error
     * @param source the source of the error
     * @param stacktrace the error stacktrace information
     * @param attributes additional custom attributes to attach to the error
     */
    fun addErrorWithStacktrace(
        message: String,
        source: RumErrorSource,
        stacktrace: String?,
        attributes: Map<String, Any?>
    )

    /**
     * Adds a specific timing in the active View. The timing duration will be computed as the
     * difference between the time the View was started and the time this function was called.
     * @param name the name of the new custom timing attribute. Timings can be
     * nested up to 8 levels deep. Names using more than 8 levels will be sanitized by SDK.
     */
    fun addTiming(
        name: String
    )

    /**
     * Adds result of evaluating a feature flag to the view.
     * Feature flag evaluations are local to the active view and are cleared when the view is stopped.
     * @param name the name of the feature flag
     * @param value the value the feature flag evaluated to
     */
    fun addFeatureFlagEvaluation(
        name: String,
        value: Any
    )

    /**
     * Adds a global attribute to all future RUM events.
     * @param key the attribute key (non null)
     * @param value the attribute value (or null)
     */
    fun addAttribute(key: String, value: Any?)

    /**
     * Removes a global attribute from all future RUM events.
     * @param key the attribute key (non null)
     */
    fun removeAttribute(key: String)

    /**
     * @return the global attributes added to this monitor
     */
    fun getAttributes(): Map<String, Any?>

    /**
     * Clear all the global attributes added to this monitor.
     */
    fun clearAttributes()

    /**
     * Stops the current session.
     * A new session will start in response to a call to `startView`, `addAction`, or
     * `startAction`. If the session is started because of a call to `addAction`,
     * or `startAction`, the last know view is restarted in the new session.
     */
    fun stopSession()

    /**
     * Utility setting to inspect the active RUM View.
     * If set, a debugging outline will be displayed on top of the application, describing the name
     * of the active RUM View in the default SDK instance (if any).
     * May be used to debug issues with RUM instrumentation in your app.
     *
     * Default value is `false`.
     */
    var debug: Boolean

    /**
     * For Datadog internal use only.
     *
     * @see _RumInternalProxy
     */
    @Suppress("FunctionName")
    @JvmSynthetic
    fun _getInternal(): _RumInternalProxy?

    // region Builder

    /**
     * A Builder class for a [RumMonitor].
     *
     * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
     */
    class Builder @JvmOverloads constructor(private val sdkCore: SdkCore = Datadog.getInstance()) {

        private var sampleRate: Float? = null
        private var sessionListener: RumSessionListener? = null

        /**
         * Sets the sample rate for RUM Sessions.
         *
         * @param sampleRate the sample rate must be a value between 0 and 100. A value of 0
         * means no RUM event will be sent, 100 means all sessions will be kept.
         */
        fun setSessionSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            this.sampleRate = sampleRate
            return this
        }

        /**
         * Sets the Session listener.
         * @param sessionListener the listener to notify whenever a new Session starts.
         */
        fun setSessionListener(sessionListener: RumSessionListener): Builder {
            this.sessionListener = sessionListener
            return this
        }

        /**
         * Builds a [RumMonitor] based on the current state of this Builder.
         */
        fun build(): RumMonitor {
            if (sdkCore !is InternalSdkCore) {
                val logger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
                logger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { UNEXPECTED_SDK_CORE_TYPE }
                )
                return NoOpRumMonitor()
            }

            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                ?.unwrap<RumFeature>()
            return if (rumFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { RUM_NOT_ENABLED_ERROR_MESSAGE }
                )
                NoOpRumMonitor()
            } else if (rumFeature.applicationId.isBlank()) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { INVALID_APPLICATION_ID_ERROR_MESSAGE }
                )
                NoOpRumMonitor()
            } else {
                DatadogRumMonitor(
                    applicationId = rumFeature.applicationId,
                    sdkCore = sdkCore,
                    sampleRate = sampleRate ?: rumFeature.sampleRate,
                    writer = rumFeature.dataWriter,
                    handler = Handler(Looper.getMainLooper()),
                    telemetryEventHandler = TelemetryEventHandler(
                        sdkCore = sdkCore,
                        eventSampler = RateBasedSampler(rumFeature.telemetrySampleRate),
                        configurationExtraSampler = RateBasedSampler(
                            rumFeature.telemetryConfigurationSampleRate
                        )
                    ),
                    firstPartyHostHeaderTypeResolver = sdkCore.firstPartyHostResolver,
                    cpuVitalMonitor = rumFeature.cpuVitalMonitor,
                    memoryVitalMonitor = rumFeature.memoryVitalMonitor,
                    frameRateVitalMonitor = rumFeature.frameRateVitalMonitor,
                    backgroundTrackingEnabled = rumFeature.backgroundEventTracking,
                    trackFrustrations = rumFeature.trackFrustrations,
                    sessionListener = sessionListener
                )
            }
        }

        internal companion object {
            internal const val UNEXPECTED_SDK_CORE_TYPE =
                "SDK instance provided doesn't implement InternalSdkCore."
            internal const val RUM_NOT_ENABLED_ERROR_MESSAGE =
                "You're trying to create a RumMonitor instance, " +
                    "but the SDK was not initialized or RUM feature was disabled " +
                    "in your Configuration. No RUM data will be sent."
            internal const val INVALID_APPLICATION_ID_ERROR_MESSAGE =
                "You're trying to create a RumMonitor instance, " +
                    "but the RUM application id was empty. No RUM data will be sent."
        }
    }
}
