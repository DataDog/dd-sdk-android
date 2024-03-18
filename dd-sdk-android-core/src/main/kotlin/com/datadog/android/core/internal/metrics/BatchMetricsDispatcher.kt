/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.advanced.FeatureFileOrchestrator
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.privacy.TrackingConsent
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal class BatchMetricsDispatcher(
    featureName: String,
    private val uploadConfiguration: DataUploadConfiguration,
    private val filePersistenceConfig: FilePersistenceConfig,
    private val internalLogger: InternalLogger,
    private val dateTimeProvider: TimeProvider,
    private val sampler: Sampler = RateBasedSampler(METRICS_DISPATCHER_DEFAULT_SAMPLING_RATE)

) : MetricsDispatcher, ProcessLifecycleMonitor.Callback {

    private val trackName: String? = resolveTrackName(featureName)
    private val isInBackground = AtomicBoolean(true)

    // region MetricsDispatcher

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

    override fun sendBatchClosedMetric(batchFile: File, batchMetadata: BatchClosedMetadata) {
        if (trackName == null || !sampler.sample() || !batchFile.existsSafe(internalLogger)) {
            return
        }
        resolveBatchClosedMetricAttributes(batchFile, batchMetadata)?.let {
            internalLogger.logMetric(
                messageBuilder = { BATCH_CLOSED_MESSAGE },
                additionalProperties = it
            )
        }
    }

    // endregion

    // region ProcessLifecycleMonitor.Callback
    override fun onStarted() {
        // NO - OP
    }

    override fun onResumed() {
        isInBackground.set(false)
    }

    override fun onStopped() {
        // NO - OP
    }

    override fun onPaused() {
        isInBackground.set(true)
    }

    // endregion

    // region Internal

    @SuppressWarnings("ReturnCount")
    private fun resolveBatchDeletedMetricAttributes(
        file: File,
        deletionReason: RemovalReason
    ): Map<String, Any?>? {
        val fileCreationTimestamp = file.nameAsTimestampSafe(internalLogger) ?: return null
        val fileAgeInMillis = dateTimeProvider.getDeviceTimestamp() - fileCreationTimestamp
        if (fileAgeInMillis < 0) {
            // the device time was manually modified or the time zone changed
            // we are dropping this metric to not skew our charts
            return null
        }
        return mapOf(
            TRACK_KEY to trackName,
            TYPE_KEY to BATCH_DELETED_TYPE_VALUE,
            BATCH_AGE_KEY to fileAgeInMillis,
            UPLOADER_DELAY_KEY to mapOf(
                UPLOADER_DELAY_MIN_KEY to uploadConfiguration.minDelayMs,
                UPLOADER_DELAY_MAX_KEY to uploadConfiguration.maxDelayMs
            ),
            UPLOADER_WINDOW_KEY to filePersistenceConfig.recentDelayMs,

            BATCH_REMOVAL_KEY to deletionReason.toString(),
            IN_BACKGROUND_KEY to isInBackground.get(),
            TRACKING_CONSENT_KEY to file.resolveFileOriginAsConsent(),
            FILE_NAME to file.name,
            THREAD_NAME to Thread.currentThread().name
        )
    }

    @SuppressWarnings("ReturnCount")
    private fun resolveBatchClosedMetricAttributes(
        file: File,
        batchMetadata: BatchClosedMetadata
    ): Map<String, Any?>? {
        val fileCreationTimestamp = file.nameAsTimestampSafe(internalLogger) ?: return null
        val batchDurationInMs = batchMetadata.lastTimeWasUsedInMs - fileCreationTimestamp
        if (batchDurationInMs < 0) {
            // the device time was manually modified or the time zone changed
            // we are dropping this metric to not skew our charts
            return null
        }
        return mapOf(
            TRACK_KEY to trackName,
            TYPE_KEY to BATCH_CLOSED_TYPE_VALUE,
            BATCH_DURATION_KEY to batchDurationInMs,
            UPLOADER_WINDOW_KEY to filePersistenceConfig.recentDelayMs,
            // we will send the telemetry even if the file is broken as it will still
            // be sent as a batch_delete telemetry later
            BATCH_SIZE_KEY to file.lengthSafe(internalLogger),
            BATCH_EVENTS_COUNT_KEY to batchMetadata.eventsCount,
            FORCE_NEW_KEY to batchMetadata.forcedNew,
            TRACKING_CONSENT_KEY to file.resolveFileOriginAsConsent(),
            FILE_NAME to file.name,
            THREAD_NAME to Thread.currentThread().name
        )
    }

    private fun File.nameAsTimestampSafe(logger: InternalLogger): Long? {
        val timestamp = this.name.toLongOrNull()
        if (timestamp == null) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { WRONG_FILE_NAME_MESSAGE_FORMAT.format(Locale.ENGLISH, this.name) }
            )
        }
        return timestamp
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

    private fun File.resolveFileOriginAsConsent(): String? {
        val fileDirectory = this.parentFile?.name ?: return null
        return if (fileDirectory.matches(FeatureFileOrchestrator.IS_PENDING_DIR_REG_EX)) {
            TrackingConsent.PENDING.toString().lowercase(Locale.US)
        } else if (fileDirectory.matches(FeatureFileOrchestrator.IS_GRANTED_DIR_REG_EX)) {
            TrackingConsent.GRANTED.toString().lowercase(Locale.US)
        } else {
            null
        }
    }

    // endregion

    companion object {

        internal const val RUM_TRACK_NAME = "rum"
        internal const val LOGS_TRACK_NAME = "logs"
        internal const val TRACE_TRACK_NAME = "trace"
        internal const val SR_TRACK_NAME = "sr"

        private const val METRICS_DISPATCHER_DEFAULT_SAMPLING_RATE = 15f

        internal const val WRONG_FILE_NAME_MESSAGE_FORMAT =
            "Unable to parse the file name as a timestamp: %s"

        // region COMMON METRIC KEYS

        /* The key for the type of the metric.*/
        internal const val TYPE_KEY = "metric_type"

        /* The key for the track name.*/
        internal const val TRACK_KEY = "track"

        /* The default duration since last write (in ms) after which the uploader considers
         the file to be "ready for upload".*/
        internal const val UPLOADER_WINDOW_KEY = "uploader_window"

        // endregion

        // region BATCH DELETE METRIC KEYS AND VALUES

        /* The key for uploader's delay options.*/
        internal const val UPLOADER_DELAY_KEY = "uploader_delay"

        /* The min delay of uploads for this track (in ms).*/
        internal const val UPLOADER_DELAY_MAX_KEY = "max"

        /* The min delay of uploads for this track (in ms).*/
        internal const val UPLOADER_DELAY_MIN_KEY = "min"

        /* The duration from batch creation to batch deletion (in ms).*/
        internal const val BATCH_AGE_KEY = "batch_age"

        /* The reason of batch deletion. */
        internal const val BATCH_REMOVAL_KEY = "batch_removal_reason"

        /* If the batch was deleted in the background. */
        internal const val IN_BACKGROUND_KEY = "in_background"

        internal const val BATCH_DELETED_MESSAGE = "[Mobile Metric] Batch Deleted"

        /* The value for the type of the metric.*/
        internal const val BATCH_DELETED_TYPE_VALUE = "batch deleted"

        // endregion

        // region BATCH CLOSE METRIC KEYS AND VALUES

        /* The size of batch at closing (in bytes). */
        internal const val BATCH_SIZE_KEY = "batch_size"

        /* The number of events written to this batch before closing.*/
        internal const val BATCH_EVENTS_COUNT_KEY = "batch_events_count"

        /* The duration from batch creation to batch closing (in ms).*/
        internal const val BATCH_DURATION_KEY = "batch_duration"

        /* If the batch was closed by core or after new batch was forced by the feature.*/
        internal const val FORCE_NEW_KEY = "forced_new"

        internal const val BATCH_CLOSED_MESSAGE = "[Mobile Metric] Batch Closed"

        /* The value for the type of the metric.*/
        internal const val BATCH_CLOSED_TYPE_VALUE = "batch closed"

        /* The value of the tracking consent according with this file origin.*/
        internal const val TRACKING_CONSENT_KEY = "consent"

        /* The file name.*/
        internal const val FILE_NAME = "filename"

        /* The thread name from which the current metric was sent.*/
        internal const val THREAD_NAME = "thread"

        // endregion
    }
}
