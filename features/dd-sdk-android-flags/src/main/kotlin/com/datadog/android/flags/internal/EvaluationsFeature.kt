/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.flags.internal.net.EvaluationsRequestFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flags Evaluations sub-feature class, registered by FlagsFeature.
 *
 * This feature handles aggregation and storage of flag evaluation events,
 * separate from exposure logging. Events are uploaded to /api/v2/flagevaluations.
 */
internal class EvaluationsFeature(
    private val sdkCore: FeatureSdkCore,
    customEvaluationEndpoint: String?
) : StorageBackedFeature {

    internal val initialized = AtomicBoolean(false)

    // region Feature

    @Suppress("TodoWithoutTask") // Will be done before r4r
    override val name: String = Feature.FLAGS_EVALUATIONS_FEATURE_NAME

    override val requestFactory: RequestFactory = EvaluationsRequestFactory(
        internalLogger = sdkCore.internalLogger,
        customEvaluationEndpoint = customEvaluationEndpoint
    )

    override fun onInitialize(appContext: Context) {
        if (initialized.get()) {
            return
        }

        initialized.set(true)
    }

    override fun onStop() {
        initialized.set(false)
    }

    /**
     * Uses the default storage configuration with standard batch size (500 items per batch).
     */
    override val storageConfiguration =
        FeatureStorageConfiguration.DEFAULT.copy(
            maxItemsPerBatch = MAX_ITEMS_PER_BATCH
        )

    // endregion

    internal companion object {
        const val MAX_ITEMS_PER_BATCH = 500
    }
}
