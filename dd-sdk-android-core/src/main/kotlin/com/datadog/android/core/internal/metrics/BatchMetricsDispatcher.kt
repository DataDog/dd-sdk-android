/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import java.io.File
import java.util.Locale

internal class BatchMetricsDispatcher(
    featureName: String,
    private val uploadConfiguration: DataUploadConfiguration,
    private val filePersistenceConfig: FilePersistenceConfig,
    private val internalLogger: InternalLogger,
    private val dateTimeProvider: TimeProvider,
    private val sampler: Sampler = RateBasedSampler(METRICS_DISPATCHER_DEFAULT_SAMPLING_RATE)
) :
    MetricsDispatcher {

    private val trackName: String? = resolveTrackName(featureName)

    override fun sendBatchDeletedMetric(batchFile: File, removalReason: RemovalReason) {
        if (!removalReason.includeInMetrics() || trackName == null || !sampler.sample()) {
            return
        }
        resolveBatchDeletedMetricAttributes(batchFile, removalReason)?.let {
            internalLogger.logMetric(
                messageBuilder = { BATCH_DELETED_MESSAGE },
                additionalProperties = it
            )
        }
    }

    private fun resolveBatchDeletedMetricAttributes(
        file: File,
        deletionReason: RemovalReason
    ): Map<String, Any?>? {
        val fileAgeInMillis = resolveFileAge(file)
        if (fileAgeInMillis < 0) {
            return null
        }
        return mutableMapOf<String, Any?>(
            TRACK_KEY to trackName,
            TYPE_KEY to BATCH_DELETED_TYPE_VALUE,
            BATCH_AGE_KEY to fileAgeInMillis,
            UPLOADER_DELAY_KEY to mapOf(
                UPLOADER_DELAY_MIN_KEY to uploadConfiguration.minDelayMs,
                UPLOADER_DELAY_MAX_KEY to uploadConfiguration.maxDelayMs
            ),
            UPLOADER_WINDOW_KEY to filePersistenceConfig.recentDelayMs,

            BATCH_REMOVAL_KEY to deletionReason.toString(),
            // TODO: RUMM-952 add inBackground flag value
            IN_BACKGROUND_KEY to false
        )
    }

    private fun resolveFileAge(file: File): Long {
        return try {
            dateTimeProvider.getDeviceTimestamp() - file.name.toLong()
        } catch (e: NumberFormatException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { WRONG_FILE_NAME_MESSAGE_FORMAT.format(Locale.ENGLISH, file.name) },
                e
            )
            -1L
        }
    }

    private fun resolveTrackName(featureName: String): String? {
        return when (featureName) {
            Feature.RUM_FEATURE_NAME -> RUM_TRACK_NAME
            Feature.LOGS_FEATURE_NAME -> LOGS_TRACK_NAME
            Feature.TRACING_FEATURE_NAME -> TRACE_TRACK_NAME
            Feature.SESSION_REPLAY_FEATURE_NAME -> SR_TRACK_NAME
            else -> null
        }
    }

    companion object {

        internal const val RUM_TRACK_NAME = "rum"
        internal const val LOGS_TRACK_NAME = "logs"
        internal const val TRACE_TRACK_NAME = "trace"
        internal const val SR_TRACK_NAME = "sr"

        private const val METRICS_DISPATCHER_DEFAULT_SAMPLING_RATE = 15f

        internal const val WRONG_FILE_NAME_MESSAGE_FORMAT =
            "Unable to parse the file name as a timestamp: %s"

        internal const val BATCH_DELETED_MESSAGE: String = "[Mobile Metric] Batch Deleted"

        /* The key for the type of the metric.*/
        internal const val TYPE_KEY = "metric_type"

        /* The value for the type of the metric.*/
        internal const val BATCH_DELETED_TYPE_VALUE = "batch deleted"

        /* The key for the track name.*/
        internal const val TRACK_KEY = "track"

        /* The key for uploader's delay options.*/
        internal const val UPLOADER_DELAY_KEY = "uploader_delay"

        /* The min delay of uploads for this track (in ms).*/
        internal const val UPLOADER_DELAY_MIN_KEY = "min"

        /* The min delay of uploads for this track (in ms).*/
        internal const val UPLOADER_DELAY_MAX_KEY = "max"

        /* The default duration since last write (in ms) after which the uploader considers
         the file to be "ready for upload".*/
        internal const val UPLOADER_WINDOW_KEY = "uploader_window"

        /* The duration from batch creation to batch deletion (in ms).*/
        internal const val BATCH_AGE_KEY = "batch_age"

        /* The reason of batch deletion. */
        internal const val BATCH_REMOVAL_KEY = "batch_removal_reason"

        /* If the batch was deleted in the background. */
        internal const val IN_BACKGROUND_KEY = "in_background"
    }
}
