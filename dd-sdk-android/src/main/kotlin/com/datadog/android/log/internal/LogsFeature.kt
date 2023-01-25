/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.internal.domain.event.LogEventSerializer
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import com.datadog.android.v2.log.internal.net.LogsRequestFactory
import com.datadog.android.v2.log.internal.storage.LogsDataWriter
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class LogsFeature(
    private val configuration: Configuration.Feature.Logs
) : StorageBackedFeature, FeatureEventReceiver {

    internal var dataWriter: DataWriter<LogEvent> = NoOpDataWriter()
    internal lateinit var sdkCore: SdkCore
    internal val initialized = AtomicBoolean(false)
    private val logGenerator = DatadogLogGenerator()

    // region Feature

    override val name: String = LOGS_FEATURE_NAME

    override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
        this.sdkCore = sdkCore
        sdkCore.setEventReceiver(LOGS_FEATURE_NAME, this)

        dataWriter = createDataWriter(configuration)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory = LogsRequestFactory(configuration.endpointUrl)
    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeEventReceiver(LOGS_FEATURE_NAME)
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    // endregion

    // region FeatureEventReceiver

    @AnyThread
    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName)
            )
            return
        }

        if (event[TYPE_EVENT_KEY] == "jvm_crash") {
            sendJvmCrashLog(event)
        } else if (event[TYPE_EVENT_KEY] == "ndk_crash") {
            sendNdkCrashLog(event)
        } else if (event[TYPE_EVENT_KEY] == "span_log") {
            sendSpanLog(event)
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event[TYPE_EVENT_KEY])
            )
        }
    }

    // endregion

    // region Internal

    private fun createDataWriter(
        configuration: Configuration.Feature.Logs
    ): DataWriter<LogEvent> {
        return LogsDataWriter(
            serializer = MapperSerializer(
                LogEventMapperWrapper(configuration.logsEventMapper),
                LogEventSerializer()
            ),
            internalLogger = internalLogger
        )
    }

    @Suppress("ComplexMethod")
    private fun sendJvmCrashLog(data: Map<*, *>) {
        val threadName = data[THREAD_NAME_EVENT_KEY] as? String
        val throwable = data[THROWABLE_EVENT_KEY] as? Throwable
        val timestamp = data[TIMESTAMP_EVENT_KEY] as? Long
        val message = data[MESSAGE_EVENT_KEY] as? String
        val loggerName = data[LOGGER_NAME_EVENT_KEY] as? String

        @Suppress("ComplexCondition")
        if (threadName == null || throwable == null ||
            timestamp == null || message == null || loggerName == null
        ) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
            return
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // argument is good
        val lock = CountDownLatch(1)

        sdkCore.getFeature(LOGS_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val log = logGenerator.generateLog(
                    DatadogLogGenerator.CRASH,
                    datadogContext = datadogContext,
                    attachNetworkInfo = true,
                    loggerName = loggerName,
                    message = message,
                    throwable = throwable,
                    attributes = emptyMap(),
                    timestamp = timestamp,
                    bundleWithTraces = true,
                    bundleWithRum = true,
                    networkInfo = null,
                    userInfo = null,
                    threadName = threadName,
                    tags = emptySet()
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                dataWriter.write(eventBatchWriter, log)
                lock.countDown()
            }

        try {
            lock.await(MAX_WRITE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Log event write operation wait was interrupted.",
                e
            )
        }
    }

    @Suppress("ComplexMethod")
    private fun sendNdkCrashLog(data: Map<*, *>) {
        val timestamp = data[TIMESTAMP_EVENT_KEY] as? Long
        val message = data[MESSAGE_EVENT_KEY] as? String
        val loggerName = data[LOGGER_NAME_EVENT_KEY] as? String
        val attributes = (data[ATTRIBUTES_EVENT_KEY] as? Map<*, *>)
            ?.filterKeys { it is String }
            ?.mapKeys { it.key as String }
        val networkInfo = data[NETWORK_INFO_EVENT_KEY] as? NetworkInfo
        val userInfo = data[USER_INFO_EVENT_KEY] as? UserInfo

        @Suppress("ComplexCondition")
        if (loggerName == null || message == null || timestamp == null || attributes == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
            return
        }

        sdkCore.getFeature(LOGS_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val log = logGenerator.generateLog(
                    DatadogLogGenerator.CRASH,
                    datadogContext = datadogContext,
                    attachNetworkInfo = true,
                    loggerName = loggerName,
                    message = message,
                    throwable = null,
                    attributes = attributes,
                    timestamp = timestamp,
                    bundleWithTraces = false,
                    bundleWithRum = false,
                    networkInfo = networkInfo,
                    userInfo = userInfo,
                    threadName = Thread.currentThread().name,
                    tags = emptySet()
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                dataWriter.write(eventBatchWriter, log)
            }
    }

    private fun sendSpanLog(data: Map<*, *>) {
        val timestamp = data[TIMESTAMP_EVENT_KEY] as? Long
        val message = data[MESSAGE_EVENT_KEY] as? String
        val loggerName = data[LOGGER_NAME_EVENT_KEY] as? String
        val attributes = (data[ATTRIBUTES_EVENT_KEY] as? Map<*, *>)
            ?.filterKeys { it is String }
            ?.mapKeys { it.key as String }

        @Suppress("ComplexCondition")
        if (loggerName == null || message == null || attributes == null || timestamp == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
            return
        }

        sdkCore.getFeature(LOGS_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val log = logGenerator.generateLog(
                    Log.VERBOSE,
                    datadogContext = datadogContext,
                    attachNetworkInfo = true,
                    loggerName = loggerName,
                    message = message,
                    throwable = null,
                    attributes = attributes,
                    timestamp = timestamp,
                    // false, because span log event will already have the necessary attributes
                    bundleWithTraces = false,
                    bundleWithRum = true,
                    threadName = Thread.currentThread().name,
                    tags = emptySet()
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                dataWriter.write(eventBatchWriter, log)
            }
    }

    // endregion

    companion object {

        private const val TYPE_EVENT_KEY = "type"
        private const val TIMESTAMP_EVENT_KEY = "timestamp"
        private const val LOGGER_NAME_EVENT_KEY = "loggerName"
        private const val ATTRIBUTES_EVENT_KEY = "attributes"
        private const val MESSAGE_EVENT_KEY = "message"
        private const val THROWABLE_EVENT_KEY = "throwable"
        private const val USER_INFO_EVENT_KEY = "userInfo"
        private const val NETWORK_INFO_EVENT_KEY = "networkInfo"
        private const val THREAD_NAME_EVENT_KEY = "threadName"

        internal const val LOGS_FEATURE_NAME = "logs"
        internal const val UNSUPPORTED_EVENT_TYPE =
            "Logs feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Logs feature received an event with unknown value of \"type\" property=%s."
        internal const val JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING =
            "Logs feature received a JVM crash event where" +
                " one or more mandatory (loggerName, throwable, message, timestamp," +
                " threadName) fields are either missing or have wrong type."
        internal const val NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING =
            "Logs feature received a NDK crash event where" +
                " one or more mandatory (loggerName, message, timestamp, attributes)" +
                " fields are either missing or have wrong type."
        internal const val SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING =
            "Logs feature received a Span log event where" +
                " one or more mandatory (loggerName, message, timestamp, attributes)" +
                " fields are either missing or have wrong type."

        internal const val MAX_WRITE_WAIT_TIMEOUT_MS = 500L
    }
}
