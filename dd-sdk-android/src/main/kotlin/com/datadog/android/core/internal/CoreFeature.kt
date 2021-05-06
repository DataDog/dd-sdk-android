/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.net.GzipRequestInterceptor
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NetworkInfoDeserializer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileHandler
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.LoggingSyncListener
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfoDeserializer
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.ndk.NdkCrashHandler
import com.datadog.android.rum.internal.ndk.NdkCrashLogDeserializer
import com.datadog.android.rum.internal.ndk.NdkNetworkInfoDataWriter
import com.datadog.android.rum.internal.ndk.NdkUserInfoDataWriter
import com.datadog.android.rum.internal.ndk.NoOpNdkCrashHandler
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal object CoreFeature {

    // region Constants

    internal val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
    private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
    private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive

    // this is a default source to be used when uploading RUM/Logs/Span data, however there is a
    // possibility to override it which is useful when SDK is used via bridge, say
    // from React Native integration
    internal const val DEFAULT_SOURCE_NAME = "android"

    // endregion

    internal val initialized = AtomicBoolean(false)
    internal var contextRef: WeakReference<Context?> = WeakReference(null)

    internal var firstPartyHostDetector = FirstPartyHostDetector(emptyList())
    internal var networkInfoProvider: NetworkInfoProvider = NoOpNetworkInfoProvider()
    internal var systemInfoProvider: SystemInfoProvider = NoOpSystemInfoProvider()
    internal var timeProvider: TimeProvider = NoOpTimeProvider()
    internal var trackingConsentProvider: ConsentProvider = NoOpConsentProvider()
    internal var userInfoProvider: MutableUserInfoProvider = NoOpMutableUserInfoProvider()

    internal var okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    internal lateinit var kronosClock: KronosClock

    internal var clientToken: String = ""
    internal var packageName: String = ""
    internal var packageVersion: String = ""
    internal var serviceName: String = ""
    internal var sourceName: String = DEFAULT_SOURCE_NAME
    internal var rumApplicationId: String? = null
    internal var isMainProcess: Boolean = true
    internal var envName: String = ""
    internal var variant: String = ""
    internal var batchSize: BatchSize = BatchSize.MEDIUM
    internal var uploadFrequency: UploadFrequency = UploadFrequency.AVERAGE
    internal var ndkCrashHandler: NdkCrashHandler = NoOpNdkCrashHandler()

    internal lateinit var uploadExecutorService: ScheduledThreadPoolExecutor
    internal lateinit var persistenceExecutorService: ExecutorService

    fun initialize(
        appContext: Context,
        credentials: Credentials,
        configuration: Configuration.Core,
        consent: TrackingConsent
    ) {

        if (initialized.get()) {
            return
        }
        readConfigurationSettings(configuration)
        readApplicationInformation(appContext, credentials)
        resolveIsMainProcess(appContext)
        initializeClockSync(appContext)
        setupOkHttpClient(configuration.needsClearTextHttp)
        firstPartyHostDetector.addKnownHosts(configuration.firstPartyHosts)
        setupExecutors()
        // BIG NOTE !!
        // Please do not move the block bellow.
        // The NDK crash handler `prepareData` function needs to be called exactly at this moment
        // to make sure it is the first task that goes in the persistence ExecutorService.
        // Because all our persisting components are working asynchronously this will avoid
        // having corrupted data (data from previous process over - written in this process into the
        // ndk crash folder before the crash was actually handled)
        prepareNdkCrashData(appContext)
        setupInfoProviders(appContext, consent)
        initialized.set(true)
    }

    fun stop() {
        if (initialized.get()) {
            contextRef.get()?.let {
                networkInfoProvider.unregister(it)
                systemInfoProvider.unregister(it)
            }
            contextRef.clear()

            trackingConsentProvider.unregisterAllCallbacks()

            cleanupApplicationInfo()
            cleanupProviders()
            shutDownExecutors()
            initialized.set(false)
            ndkCrashHandler = NoOpNdkCrashHandler()
        }
    }

    fun buildFilePersistenceConfig(): FilePersistenceConfig {
        return FilePersistenceConfig(
            recentDelayMs = batchSize.windowDurationMs
        )
    }

    fun drainAndShutdownExecutors() {
        val tasks = arrayListOf<Runnable>()
        (persistenceExecutorService as? ThreadPoolExecutor)
            ?.queue
            ?.drainTo(tasks)
        // we make sure we upload the currently locked files
        uploadExecutorService
            .queue
            .drainTo(tasks)
        // we need to make sure we drain the runnables in both executors first
        // then we shut them down by using the await termination method to make sure we block
        // the thread until the active task is finished.
        persistenceExecutorService.shutdown()
        uploadExecutorService.shutdown()
        persistenceExecutorService.awaitTermination(10, TimeUnit.SECONDS)
        uploadExecutorService.awaitTermination(10, TimeUnit.SECONDS)
        tasks.forEach {
            it.run()
        }
    }

    // region Internal

    private fun prepareNdkCrashData(appContext: Context) {
        if (isMainProcess) {
            ndkCrashHandler = DatadogNdkCrashHandler(
                appContext,
                persistenceExecutorService,
                LogGenerator(
                    serviceName,
                    DatadogNdkCrashHandler.LOGGER_NAME,
                    networkInfoProvider,
                    userInfoProvider,
                    envName,
                    packageVersion
                ),
                NdkCrashLogDeserializer(sdkLogger),
                RumEventDeserializer(),
                NetworkInfoDeserializer(sdkLogger),
                UserInfoDeserializer(sdkLogger),
                sdkLogger
            )
            ndkCrashHandler.prepareData()
        }
    }

    private fun initializeClockSync(appContext: Context) {
        kronosClock = AndroidClockFactory.createKronosClock(
            appContext,
            ntpHosts = listOf(
                DatadogEndpoint.NTP_0,
                DatadogEndpoint.NTP_1,
                DatadogEndpoint.NTP_2,
                DatadogEndpoint.NTP_3
            ),
            cacheExpirationMs = TimeUnit.MINUTES.toMillis(30),
            minWaitTimeBetweenSyncMs = TimeUnit.MINUTES.toMillis(5),
            syncListener = LoggingSyncListener()
        ).apply { syncInBackground() }
    }

    private fun readApplicationInformation(appContext: Context, credentials: Credentials) {
        packageName = appContext.packageName
        packageVersion = appContext.packageManager.getPackageInfo(packageName, 0).let {
            // we need to use the deprecated method because getLongVersionCode method is only
            // available from API 28 and above
            @Suppress("DEPRECATION")
            it.versionName ?: it.versionCode.toString()
        }
        clientToken = credentials.clientToken
        serviceName = credentials.serviceName ?: appContext.packageName
        rumApplicationId = credentials.rumApplicationId
        envName = credentials.envName
        variant = credentials.variant
        contextRef = WeakReference(appContext)
    }

    private fun readConfigurationSettings(configuration: Configuration.Core) {
        batchSize = configuration.batchSize
        uploadFrequency = configuration.uploadFrequency
    }

    private fun setupInfoProviders(
        appContext: Context,
        consent: TrackingConsent
    ) {
        // Tracking Consent Provider
        trackingConsentProvider = TrackingConsentProvider(consent)

        // Time Provider
        timeProvider = KronosTimeProvider(kronosClock)

        // System Info Provider
        systemInfoProvider = BroadcastReceiverSystemInfoProvider()
        systemInfoProvider.register(appContext)

        // Network Info Provider
        setupNetworkInfoProviders(appContext)

        // User Info Provider
        setupUserInfoProvider(appContext)
    }

    private fun setupUserInfoProvider(
        appContext: Context
    ) {
        val userInfoWriter = ScheduledWriter(
            NdkUserInfoDataWriter(
                appContext,
                trackingConsentProvider,
                persistenceExecutorService,
                BatchFileHandler(sdkLogger),
                sdkLogger
            ),
            persistenceExecutorService,
            sdkLogger
        )
        userInfoProvider = DatadogUserInfoProvider(userInfoWriter)
    }

    private fun setupNetworkInfoProviders(
        appContext: Context
    ) {
        val networkInfoWriter = ScheduledWriter(
            NdkNetworkInfoDataWriter(
                appContext,
                trackingConsentProvider,
                persistenceExecutorService,
                BatchFileHandler(sdkLogger),
                sdkLogger
            ),
            persistenceExecutorService,
            sdkLogger
        )
        networkInfoProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CallbackNetworkInfoProvider(networkInfoWriter)
        } else {
            BroadcastReceiverNetworkInfoProvider(networkInfoWriter)
        }
        networkInfoProvider.register(appContext)
    }

    private fun setupOkHttpClient(needsClearTextHttp: Boolean) {
        val connectionSpec = when {
            needsClearTextHttp -> ConnectionSpec.CLEARTEXT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> ConnectionSpec.RESTRICTED_TLS
            else -> ConnectionSpec.MODERN_TLS
        }

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GzipRequestInterceptor())
            .callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))
            .build()
    }

    private fun setupExecutors() {
        uploadExecutorService = ScheduledThreadPoolExecutor(CORE_DEFAULT_POOL_SIZE)
        persistenceExecutorService = ThreadPoolExecutor(
            CORE_DEFAULT_POOL_SIZE,
            Runtime.getRuntime().availableProcessors(),
            THREAD_POOL_MAX_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque()
        )
    }

    private fun resolveIsMainProcess(appContext: Context) {
        val currentProcessId = Process.myPid()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val currentProcess = manager?.runningAppProcesses?.firstOrNull {
            it.pid == currentProcessId
        }
        isMainProcess = if (currentProcess == null) {
            true
        } else {
            appContext.packageName == currentProcess.processName
        }
    }

    private fun shutDownExecutors() {
        uploadExecutorService.shutdownNow()
        persistenceExecutorService.shutdownNow()

        uploadExecutorService.awaitTermination(1, TimeUnit.SECONDS)
        persistenceExecutorService.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun cleanupApplicationInfo() {
        clientToken = ""
        packageName = ""
        packageVersion = ""
        serviceName = ""
        sourceName = DEFAULT_SOURCE_NAME
        rumApplicationId = null
        isMainProcess = true
        envName = ""
        variant = ""
    }

    private fun cleanupProviders() {
        firstPartyHostDetector = FirstPartyHostDetector(emptyList())
        networkInfoProvider = NoOpNetworkInfoProvider()
        systemInfoProvider = NoOpSystemInfoProvider()
        timeProvider = NoOpTimeProvider()
        trackingConsentProvider = NoOpConsentProvider()
        userInfoProvider = NoOpMutableUserInfoProvider()
    }

    // endregion
}
