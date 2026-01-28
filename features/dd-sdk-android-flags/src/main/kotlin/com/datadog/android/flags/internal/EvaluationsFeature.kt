/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.aggregation.DDContext
import com.datadog.android.flags.internal.net.EvaluationsRequestFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flags Evaluations sub-feature class, registered by FlagsFeature.
 *
 * This feature handles aggregation and storage of flag evaluation events,
 * separate from exposure logging. Events are uploaded to /api/v2/flagevaluations.
 *
 * Implements [FeatureContextUpdateReceiver] to capture RUM context (application ID,
 * view ID, view name) for use in evaluation aggregation.
 */
internal class EvaluationsFeature(
    private val sdkCore: FeatureSdkCore,
    internal val flagsConfiguration: FlagsConfiguration
) : StorageBackedFeature, FeatureContextUpdateReceiver {

    internal val initialized = AtomicBoolean(false)

    /**
     * Current RUM context captured from feature context updates.
     * Volatile to ensure visibility across threads without additional synchronization.
     */
    @Volatile
    internal var rumContext: DDContext = DDContext.EMPTY

    // region Feature

    override val name: String = Feature.FLAGS_EVALUATIONS_FEATURE_NAME
    override val storageConfiguration = FeatureStorageConfiguration.DEFAULT

    override val requestFactory: RequestFactory = EvaluationsRequestFactory(
        internalLogger = sdkCore.internalLogger,
        customEvaluationEndpoint = flagsConfiguration.customEvaluationEndpoint
    )

    override fun onInitialize(appContext: Context) {
        if (initialized.get()) {
            return
        }

        // Register for RUM context updates
        sdkCore.setContextUpdateReceiver(this)
        initialized.set(true)
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
        initialized.set(false)
    }

    // endregion

    // region FeatureContextUpdateReceiver
    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == RUM_FEATURE_NAME) {
            val applicationId = context[RUM_APPLICATION_ID] as? String
            val viewId = context[RUM_VIEW_ID] as? String
            val viewName = context[RUM_VIEW_NAME] as? String

            rumContext = DDContext(
                service = sdkCore.service,
                applicationId = applicationId,
                viewId = viewId,
                viewName = viewName
            )
        }
    }

    // endregion
}
