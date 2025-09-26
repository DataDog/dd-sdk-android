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
import com.datadog.android.flags.FlagsConfiguration

/**
 * An implementation of [Feature] for getting and reporting
 * feature flags to the RUM dashboard.
 */
internal class FlagsFeature(private val sdkCore: FeatureSdkCore, internal var flagsConfiguration: FlagsConfiguration) :
    Feature,
    FeatureContextUpdateReceiver {

    internal var applicationId: String? = null

    // region Context Receiver

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            applicationId = context["application_id"].toString()
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
    }

    // TODO: create a ReqestFactory here
    @Suppress("unused") // This will be used for exposure logging
    val customEndpointUrl: String? = flagsConfiguration.customEndpointUrl

    // TODO: create a ReqestFactory here
    @Suppress("unused") // This will be used to fetch flags.
    val flaggingProxyUrl: String? = flagsConfiguration.flaggingProxyUrl

    // endregion

    internal companion object {
        // TODO move this to com.datadog.android.api.feature.Feature
        const val FLAGS_FEATURE_NAME = "flags"
    }
}
