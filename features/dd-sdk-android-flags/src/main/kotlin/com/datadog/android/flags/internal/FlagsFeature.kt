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

/**
 * An implementation of [Feature] for getting and reporting
 * feature flags to the RUM dashboard.
 */
internal class FlagsFeature(
    private val sdkCore: FeatureSdkCore,
    internal var applicationId: String? = null
) : Feature, FeatureContextUpdateReceiver {

    override fun onContextUpdate(
        featureName: String,
        context: Map<String, Any?>
    ) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            applicationId = context["application_id"].toString()
        }
    }

    override val name: String = FLAGS_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        sdkCore.setContextUpdateReceiver(this)
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
    }

    internal companion object {
        const val FLAGS_FEATURE_NAME = "flags"
    }
}
