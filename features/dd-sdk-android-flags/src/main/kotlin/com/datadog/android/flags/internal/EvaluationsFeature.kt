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
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.aggregation.EvaluationAggregator
import com.datadog.android.flags.internal.net.EvaluationsRequestFactory
import com.datadog.android.flags.internal.storage.EvaluationEventRecordWriter
import com.datadog.android.flags.model.EvaluationContext
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
) : StorageBackedFeature,
    FeatureContextUpdateReceiver {

    /**
     * Cached Datadog context information for evaluation events.
     */
    private data class RumContext(val rumApplicationId: String?, val rumViewName: String?)

    internal val initialized = AtomicBoolean(false)

    /**
     * The evaluation events processor for aggregating flag evaluations.
     * Created during initialization and stopped during shutdown.
     */
    @Volatile
    private var evaluationProcessor: EvaluationEventsProcessor? = null

    private var scheduledExecutor: ScheduledExecutorService? = null

    /**
     * Cached context for evaluation events.
     * Updated when RUM context changes via [onContextUpdate].
     */
    @Volatile
    private var rumContext: RumContext? = null

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

        // Register for context updates (will trigger onContextUpdate immediately if RUM has context)
        sdkCore.setContextUpdateReceiver(this)

        // Create the evaluation processor
        val executor = sdkCore.createScheduledExecutorService(EXECUTOR_NAME)
        scheduledExecutor = executor

        val writer = EvaluationEventRecordWriter(sdkCore)
        val aggregator = EvaluationAggregator(DEFAULT_MAX_AGGREGATIONS)
        evaluationProcessor = EvaluationEventsProcessor(
            writer = writer,
            timeProvider = sdkCore.timeProvider,
            scheduledExecutor = executor,
            internalLogger = sdkCore.internalLogger,
            flushIntervalMs = flagsConfiguration.evaluationFlushIntervalMs,
            aggregator = aggregator
        )
        evaluationProcessor?.reschedulePeriodicFlush()

        initialized.set(true)
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)

        evaluationProcessor?.stop()
        evaluationProcessor = null

        @Suppress("UnsafeThirdPartyFunctionCall") // shutdown() is safe - Android doesn't use SecurityManager
        scheduledExecutor?.shutdown()
        scheduledExecutor = null

        rumContext = null
        initialized.set(false)
    }

    // endregion

    // region FeatureContextUpdateReceiver

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            rumContext = RumContext(
                rumApplicationId = context[RUM_APPLICATION_ID] as? String,
                rumViewName = context[RUM_VIEW_NAME] as? String
            )
        }
    }

    // endregion

    // region Evaluation Processing

    /**
     * Processes a flag evaluation event, adding the current DDContext.
     */
    internal fun processEvaluation(
        flagKey: String,
        context: EvaluationContext,
        variantKey: String?,
        allocationKey: String?,
        errorCode: String?,
        errorMessage: String?
    ) {
        val processor = evaluationProcessor ?: return
        val currentDdContext = rumContext

        processor.processEvaluation(
            flagKey = flagKey,
            context = context,
            rumApplicationId = currentDdContext?.rumApplicationId,
            rumViewName = currentDdContext?.rumViewName,
            variantKey = variantKey,
            allocationKey = allocationKey,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    // endregion

    internal companion object {
        internal const val DEFAULT_MAX_AGGREGATIONS = 1000
        private const val EXECUTOR_NAME = "flags-evaluation"
        private const val RUM_APPLICATION_ID = "application_id"
        private const val RUM_VIEW_NAME = "view_name"
    }
}
