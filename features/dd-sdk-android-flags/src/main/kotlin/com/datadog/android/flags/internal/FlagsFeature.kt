/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.internal.model.FlagsContext

/**
 * An implementation of [Feature] for getting and reporting
 * feature flags to the RUM dashboard.
 */
internal class FlagsFeature(private val sdkCore: FeatureSdkCore, internal var flagsConfiguration: FlagsConfiguration) :
    Feature,
    FeatureContextUpdateReceiver {

    internal var applicationId: String? = null

    /**
     * The complete internal configuration context for the Flags feature.
     * This combines core SDK parameters with feature-level configuration.
     * Will be null until the core SDK context is available.
     */
    internal var flagsContext: FlagsContext? = null
        private set

    // region Context Receiver

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            applicationId = context["application_id"].toString()
            updateFlagsContext()
        }
    }

    /**
     * Updates the FlagsContext when SDK context becomes available or changes.
     * This method creates the unified configuration object that other components will use.
     */
    private fun updateFlagsContext() {
        val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
        if (datadogContext != null) {
            flagsContext = FlagsContext.create(datadogContext, applicationId, flagsConfiguration)
        }
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
    }

    // endregion

    // region Feature

    override val name: String = FlagsFeature.FLAGS_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        sdkCore.setContextUpdateReceiver(this)
        // Try to initialize FlagsContext immediately if SDK context is available
        tryInitializeFlagsContext()
    }

    /**
     * Attempts to initialize FlagsContext immediately if all required context is available.
     * This handles the case where the SDK context is already available when the feature is initialized.
     */
    private fun tryInitializeFlagsContext() {
        val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
        if (datadogContext != null) {
            // For initial setup, applicationId might still be null (will be updated via RUM context later)
            flagsContext = FlagsContext.create(datadogContext, applicationId, flagsConfiguration)
        }
    }

    // endregion

    internal companion object {
        // TODO move this to com.datadog.android.api.feature.Feature
        const val FLAGS_FEATURE_NAME = "flags"
    }
}
