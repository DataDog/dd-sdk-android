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
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.net.EvaluationsRequestFactory
import com.datadog.android.flags.internal.storage.EvaluationEventRecordWriter
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flags Evaluations sub-feature class, registered by FlagsFeature.
 *
 * This feature handles aggregation and storage of flag evaluation events,
 * separate from exposure logging. Events are uploaded to /api/v2/flagevaluations.
 */
internal class EvaluationsFeature(
    private val sdkCore: FeatureSdkCore,
    internal val flagsConfiguration: FlagsConfiguration
) : StorageBackedFeature {

    internal val initialized = AtomicBoolean(false)

    /**
     * The evaluation events processor for aggregating flag evaluations.
     * Created during initialization and stopped during shutdown.
     */
    @Volatile
    internal var evaluationProcessor: EvaluationEventsProcessor? = null
        private set

    private var scheduledExecutor: ScheduledExecutorService? = null

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

        // Create the evaluation processor
        val executor = sdkCore.createScheduledExecutorService(EXECUTOR_NAME)
        scheduledExecutor = executor

        val writer = EvaluationEventRecordWriter(sdkCore)
        evaluationProcessor = EvaluationEventsProcessor(
            internalSdkCore = sdkCore as InternalSdkCore,
            writer = writer,
            timeProvider = sdkCore.timeProvider,
            scheduledExecutor = executor,
            internalLogger = sdkCore.internalLogger,
            flushIntervalMs = flagsConfiguration.evaluationFlushIntervalMs,
            maxAggregations = DEFAULT_MAX_AGGREGATIONS
        )
        evaluationProcessor?.schedulePeriodicFlush()

        initialized.set(true)
    }

    override fun onStop() {
        evaluationProcessor?.stop()
        evaluationProcessor = null

        @Suppress("UnsafeThirdPartyFunctionCall") // shutdown() is safe - Android doesn't use SecurityManager
        scheduledExecutor?.shutdown()
        scheduledExecutor = null

        initialized.set(false)
    }

    // endregion

    internal companion object {
        internal const val DEFAULT_MAX_AGGREGATIONS = 1000
        private const val EXECUTOR_NAME = "flags-evaluation"
    }
}
