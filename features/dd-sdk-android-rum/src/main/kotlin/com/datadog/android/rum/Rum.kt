/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.Datadog
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore

/**
 * An entry point to Datadog RUM feature.
 */
object Rum {

    /**
     * Enables a RUM feature based on the configuration provided.
     *
     * @param rumConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(rumConfiguration: RumConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
        val rumFeature = RumFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            applicationId = rumConfiguration.applicationId,
            configuration = rumConfiguration.featureConfiguration
        )

        sdkCore.registerFeature(rumFeature)
    }

    /**
     * Utility setting to inspect the active RUM View.
     * If set, a debugging outline will be displayed on top of the application, describing the name
     * of the active RUM View in the default SDK instance (if any).
     * May be used to debug issues with RUM instrumentation in your app.
     *
     * @param enable if enabled, then app will show an overlay describing the active RUM view.
     * @param sdkCore SDK instance to enable RUM debugging in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enableDebugging(enable: Boolean, sdkCore: SdkCore = Datadog.getInstance()) {
        val rumFeatureScope = (sdkCore as FeatureSdkCore)
            .getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeatureScope == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RUM_DEBUG_RUM_NOT_ENABLED_WARNING
            )
            return
        }

        val rumFeature = rumFeatureScope.unwrap<RumFeature>()
        if (enable) {
            rumFeature.enableDebugging()
        } else {
            rumFeature.disableDebugging()
        }
    }

    internal const val RUM_DEBUG_RUM_NOT_ENABLED_WARNING =
        "Cannot switch RUM debugging, because RUM feature is not enabled."
}
