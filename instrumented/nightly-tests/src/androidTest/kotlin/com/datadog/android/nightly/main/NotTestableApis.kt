/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.main

/**
 * apiMethodSignature: com.datadog.android._InternalProxy$_TelemetryProxy#fun debug(String)
 * apiMethodSignature: com.datadog.android._InternalProxy$_TelemetryProxy#fun error(String, String?, String?)
 * apiMethodSignature: com.datadog.android._InternalProxy$_TelemetryProxy#fun error(String, Throwable? = null)
 * apiMethodSignature: com.datadog.android._InternalProxy#fun setCustomAppVersion(String)
 * apiMethodSignature: com.datadog.android.Datadog#fun addUserProperties(Map<String, Any?>, com.datadog.android.api.SdkCore = getInstance())
 * apiMethodSignature: com.datadog.android.Datadog#fun clearAllData(com.datadog.android.api.SdkCore = getInstance())
 * apiMethodSignature: com.datadog.android.Datadog#fun isInitialized(String? = null): Boolean
 * apiMethodSignature: com.datadog.android.Datadog#fun setVerbosity(Int)
 * apiMethodSignature: com.datadog.android.Datadog#fun stopInstance(String? = null)
 * apiMethodSignature: com.datadog.android.Datadog#fun stopSession()
 * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setAdditionalConfiguration(Map<String, Any>): Builder
 * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setProxy(java.net.Proxy, okhttp3.Authenticator?): Builder
 * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setUploadFrequency(UploadFrequency): Builder
 * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setUseDeveloperModeWhenDebuggable(Boolean): Builder
 * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useSite(com.datadog.android.DatadogSite): Builder
 * apiMethodSignature: com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor#constructor(Int, com.datadog.android.api.InternalLogger)
 * apiMethodSignature: com.datadog.android.core.internal.utils.JsonSerializer#fun toJsonElement(Any?): com.google.gson.JsonElement
 * apiMethodSignature: com.datadog.android.core.sampling.RateBasedSampler#constructor(Double)
 * apiMethodSignature: com.datadog.android.core.sampling.RateBasedSampler#constructor(Float)
 * apiMethodSignature: com.datadog.android.event.MapperSerializer<T#constructor(EventMapper<T>, com.datadog.android.core.persistence.Serializer<T>)
 * apiMethodSignature: com.datadog.android.log.Logs#fun enable(LogsConfiguration, com.datadog.android.api.SdkCore = Datadog.getInstance())
 * apiMethodSignature: com.datadog.android.log.Logger#fun log(Int, String, Throwable? = null, Map<String, Any?> = emptyMap())
 * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setLogcatLogsEnabled(Boolean): Builder
 * apiMethodSignature: com.datadog.android.log.LogsConfiguration$Builder#fun useCustomEndpoint(String): Builder
 * apiMethodSignature: com.datadog.android.ndk.NdkCrashReports#fun enable(com.datadog.android.api.SdkCore = Datadog.getInstance())
 * apiMethodSignature: com.datadog.android.rum._RumInternalProxy#fun addLongTask(Long, String)
 * apiMethodSignature: com.datadog.android.rum.resource.RumResourceInputStream#constructor(java.io.InputStream, String, com.datadog.android.api.SdkCore = Datadog.getInstance())
 * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun disableUserInteractionTracking(): Builder
 * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun setAdditionalConfiguration(Map<String, Any>): Builder
 * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun setSessionListener(RumSessionListener): Builder
 * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun setTelemetrySampleRate(Float): Builder
 * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun useCustomEndpoint(String): Builder
 * apiMethodSignature: com.datadog.android.rum.tracking.NavigationViewTrackingStrategy#fun startTracking()
 * apiMethodSignature: com.datadog.android.rum.tracking.NavigationViewTrackingStrategy#fun stopTracking()
 * apiMethodSignature: com.datadog.android.trace.Trace#fun enable(TraceConfiguration, com.datadog.android.api.SdkCore = Datadog.getInstance())
 * apiMethodSignature: com.datadog.android.trace.TraceConfiguration$Builder#fun useCustomEndpoint(String): Builder
 * apiMethodSignature: com.datadog.android.trace.TraceConfiguration$Builder#fun setNetworkInfoEnabled(Boolean): Builder
 * apiMethodSignature: com.datadog.android.core.SdkReference#constructor(String? = null, (com.datadog.android.api.SdkCore) -> Unit = {})
 * apiMethodSignature: com.datadog.android.core.SdkReference#fun get(): com.datadog.android.api.SdkCore?
 * apiMethodSignature: com.datadog.android.sessionreplay.SessionReplay#fun enable(SessionReplayConfiguration, com.datadog.android.api.SdkCore = Datadog.getInstance())
 * apiMethodSignature: com.datadog.android.sessionreplay.SessionReplayConfiguration$Builder#fun addExtensionSupport(ExtensionSupport): Builder
 * apiMethodSignature: com.datadog.android.sessionreplay.SessionReplayConfiguration$Builder#fun build(): SessionReplayConfiguration
 * apiMethodSignature: com.datadog.android.sessionreplay.SessionReplayConfiguration$Builder#fun setPrivacy(SessionReplayPrivacy): Builder
 * apiMethodSignature: com.datadog.android.sessionreplay.SessionReplayConfiguration$Builder#fun useCustomEndpoint(String): Builder
 * apiMethodSignature: fun Collection<ByteArray>.join(ByteArray, ByteArray = ByteArray(0), ByteArray = ByteArray(0), com.datadog.android.api.InternalLogger): ByteArray
 * apiMethodSignature: fun java.math.BigInteger.toHexString(): String
 * apiMethodSignature: fun java.util.concurrent.Executor.executeSafe(String, com.datadog.android.api.InternalLogger, Runnable)
 * apiMethodSignature: fun java.util.concurrent.ExecutorService.submitSafe(String, com.datadog.android.api.InternalLogger, Runnable): java.util.concurrent.Future<*>?
 * apiMethodSignature: fun java.util.concurrent.ScheduledExecutorService.scheduleSafe(String, Long, java.util.concurrent.TimeUnit, com.datadog.android.api.InternalLogger, Runnable): java.util.concurrent.ScheduledFuture<*>?
 * apiMethodSignature: fun Int.toHexString(): String
 * apiMethodSignature: fun Long.toHexString(): String
 * apiMethodSignature: fun Throwable.loggableStackTrace(): String
 * apiMethodSignature: fun <T: java.io.Closeable, R> T.useMonitored(com.datadog.android.api.SdkCore = Datadog.getInstance(), (T) -> R): R
 * apiMethodSignature: fun okhttp3.Request.Builder.parentSpan(io.opentracing.Span): okhttp3.Request.Builder
 */
