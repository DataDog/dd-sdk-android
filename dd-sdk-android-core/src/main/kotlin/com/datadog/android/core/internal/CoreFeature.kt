/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.datadog.android.BuildConfig
import com.datadog.android.DatadogNtpEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.CurlInterceptor
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.net.GzipRequestInterceptor
import com.datadog.android.core.internal.net.RotatingDnsResolver
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NetworkInfoDeserializer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.persistence.JsonObjectDeserializer
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.DefaultAndroidInfoProvider
import com.datadog.android.core.internal.system.DefaultAppVersionProvider
import com.datadog.android.core.internal.system.NoOpAndroidInfoProvider
import com.datadog.android.core.internal.system.NoOpAppVersionProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.thread.LoggingThreadPoolExecutor
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.LoggingSyncListener
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.user.DatadogUserInfoProvider
import com.datadog.android.core.internal.user.MutableUserInfoProvider
import com.datadog.android.core.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.core.internal.user.UserInfoDeserializer
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.internal.utils.unboundInternalLogger
import com.datadog.android.ndk.DatadogNdkCrashHandler
import com.datadog.android.ndk.NdkCrashHandler
import com.datadog.android.ndk.NdkCrashLogDeserializer
import com.datadog.android.ndk.NdkNetworkInfoDataWriter
import com.datadog.android.ndk.NdkUserInfoDataWriter
import com.datadog.android.ndk.NoOpNdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.security.Encryption
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.DatadogContextProvider
import com.datadog.android.v2.core.internal.NoOpContextProvider
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
internal class CoreFeature(private val internalLogger: InternalLogger) {

    internal val initialized = AtomicBoolean(false)
    internal var contextRef: WeakReference<Context?> = WeakReference(null)

    internal var firstPartyHostHeaderTypeResolver =
        DefaultFirstPartyHostHeaderTypeResolver(emptyMap())
    internal var networkInfoProvider: NetworkInfoProvider = NoOpNetworkInfoProvider()
    internal var systemInfoProvider: SystemInfoProvider = NoOpSystemInfoProvider()
    internal var timeProvider: TimeProvider = NoOpTimeProvider()
    internal var trackingConsentProvider: ConsentProvider = NoOpConsentProvider()
    internal var userInfoProvider: MutableUserInfoProvider = NoOpMutableUserInfoProvider()
    internal var contextProvider: ContextProvider = NoOpContextProvider()

    internal lateinit var okHttpClient: OkHttpClient
    internal var kronosClock: KronosClock? = null

    internal var clientToken: String = ""
    internal var packageName: String = ""
    internal var packageVersionProvider: AppVersionProvider = NoOpAppVersionProvider()
    internal var serviceName: String = ""
    internal var sourceName: String = DEFAULT_SOURCE_NAME
    internal var sdkVersion: String = DEFAULT_SDK_VERSION
    internal var isMainProcess: Boolean = true
    internal var envName: String = ""
    internal var variant: String = ""
    internal var batchSize: BatchSize = BatchSize.MEDIUM
    internal var uploadFrequency: UploadFrequency = UploadFrequency.AVERAGE
    internal var ndkCrashHandler: NdkCrashHandler = NoOpNdkCrashHandler()
    internal var site: DatadogSite = DatadogSite.US1

    internal lateinit var uploadExecutorService: ScheduledThreadPoolExecutor
    internal lateinit var persistenceExecutorService: ExecutorService
    internal var localDataEncryption: Encryption? = null
    internal lateinit var storageDir: File
    internal lateinit var androidInfoProvider: AndroidInfoProvider

    internal val featuresContext: MutableMap<String, Map<String, Any?>> = ConcurrentHashMap()

    fun initialize(
        appContext: Context,
        sdkInstanceId: String,
        credentials: Credentials,
        configuration: Configuration.Core,
        consent: TrackingConsent
    ) {
        if (initialized.get()) {
            return
        }
        readConfigurationSettings(configuration)
        readApplicationInformation(appContext, credentials)
        resolveProcessInfo(appContext)
        setupExecutors()
        persistenceExecutorService.submitSafe("NTP Sync initialization", unboundInternalLogger) {
            // Kronos performs I/O operation on startup, it needs to run in background
            @Suppress("ThreadSafety") // we are in the worker thread context
            initializeClockSync(appContext)
        }
        setupOkHttpClient(configuration)
        firstPartyHostHeaderTypeResolver
            .addKnownHostsWithHeaderTypes(configuration.firstPartyHostsWithHeaderTypes)
        androidInfoProvider = DefaultAndroidInfoProvider(appContext)
        storageDir = File(
            appContext.cacheDir,
            DATADOG_STORAGE_DIR_NAME.format(Locale.US, sdkInstanceId)
        )
        // BIG NOTE !!
        // Please do not move the block bellow.
        // The NDK crash handler `prepareData` function needs to be called exactly at this moment
        // to make sure it is the first task that goes in the persistence ExecutorService.
        // Because all our persisting components are working asynchronously this will avoid
        // having corrupted data (data from previous process over - written in this process into the
        // ndk crash folder before the crash was actually handled)
        prepareNdkCrashData()
        setupInfoProviders(appContext, consent)
        initialized.set(true)
        contextProvider = DatadogContextProvider(this)
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

            try {
                kronosClock?.shutdown()
            } catch (ise: IllegalStateException) {
                // this may be called from the test
                // when Kronos is already shut down
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Trying to shut down Kronos when it is already not running" },
                    ise
                )
            }

            featuresContext.clear()

            initialized.set(false)
            ndkCrashHandler = NoOpNdkCrashHandler()
            trackingConsentProvider = NoOpConsentProvider()
            contextProvider = NoOpContextProvider()
        }
    }

    fun buildFilePersistenceConfig(): FilePersistenceConfig {
        return FilePersistenceConfig(
            recentDelayMs = batchSize.windowDurationMs
        )
    }

    @Throws(UnsupportedOperationException::class, InterruptedException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Used in Nightly tests only
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
        persistenceExecutorService.awaitTermination(DRAIN_WAIT_SECONDS, TimeUnit.SECONDS)
        uploadExecutorService.awaitTermination(DRAIN_WAIT_SECONDS, TimeUnit.SECONDS)
        tasks.forEach {
            it.run()
        }
    }

    // region Internal

    private fun prepareNdkCrashData() {
        if (isMainProcess) {
            ndkCrashHandler = DatadogNdkCrashHandler(
                storageDir,
                persistenceExecutorService,
                NdkCrashLogDeserializer(internalLogger),
                rumEventDeserializer = JsonObjectDeserializer(internalLogger),
                NetworkInfoDeserializer(internalLogger),
                UserInfoDeserializer(internalLogger),
                internalLogger,
                rumFileReader = BatchFileReaderWriter.create(internalLogger, localDataEncryption),
                envFileReader = FileReaderWriter.create(internalLogger, localDataEncryption)
            )
            ndkCrashHandler.prepareData()
        }
    }

    @WorkerThread
    private fun initializeClockSync(appContext: Context) {
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getSafeContext(appContext)
        } else {
            appContext
        }
        kronosClock = AndroidClockFactory.createKronosClock(
            safeContext,
            ntpHosts = listOf(
                DatadogNtpEndpoint.NTP_0,
                DatadogNtpEndpoint.NTP_1,
                DatadogNtpEndpoint.NTP_2,
                DatadogNtpEndpoint.NTP_3
            ).map { it.host },
            cacheExpirationMs = TimeUnit.MINUTES.toMillis(NTP_CACHE_EXPIRATION_MINUTES),
            minWaitTimeBetweenSyncMs = TimeUnit.MINUTES.toMillis(NTP_DELAY_BETWEEN_SYNCS_MINUTES),
            syncListener = LoggingSyncListener(internalLogger)
        ).apply {
            if (!disableKronosBackgroundSync) {
                try {
                    syncInBackground()
                } catch (ise: IllegalStateException) {
                    // should never happen
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "Unable to launch a synchronize local time with an NTP server." },
                        ise
                    )
                }
            }

            timeProvider = KronosTimeProvider(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getSafeContext(appContext: Context): Context {
        // When the host app uses the `directBootAware` flag on a  file encrypted device,
        // the app can wake up during the boot sequence before the device is unlocked
        // This mean any file I/O or access to shared preferences will throw an exception
        // This safe context creates a device-protected storage which can be used for non sensitive
        // data. It should not be used to store the data captured by the SDK.
        return appContext.createDeviceProtectedStorageContext() ?: appContext
    }

    private fun readApplicationInformation(appContext: Context, credentials: Credentials) {
        packageName = appContext.packageName
        packageVersionProvider = DefaultAppVersionProvider(
            getPackageInfo(appContext)?.let {
                // we need to use the deprecated method because getLongVersionCode method is only
                // available from API 28 and above
                @Suppress("DEPRECATION")
                it.versionName ?: it.versionCode.toString()
            } ?: DEFAULT_APP_VERSION
        )
        clientToken = credentials.clientToken
        serviceName = credentials.service ?: appContext.packageName
        envName = credentials.env
        variant = credentials.variant
        contextRef = WeakReference(appContext)
    }

    private fun getPackageInfo(appContext: Context): PackageInfo? {
        return try {
            with(appContext.packageManager) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    getPackageInfo(packageName, 0)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to read your application's version name" },
                e
            )
            null
        }
    }

    private fun readConfigurationSettings(configuration: Configuration.Core) {
        batchSize = configuration.batchSize
        uploadFrequency = configuration.uploadFrequency
        localDataEncryption = configuration.encryption
        site = configuration.site
    }

    private fun setupInfoProviders(
        appContext: Context,
        consent: TrackingConsent
    ) {
        // Tracking Consent Provider
        trackingConsentProvider = TrackingConsentProvider(consent)

        // System Info Provider
        systemInfoProvider = BroadcastReceiverSystemInfoProvider(internalLogger = internalLogger)
        systemInfoProvider.register(appContext)

        // Network Info Provider
        setupNetworkInfoProviders(appContext)

        // User Info Provider
        setupUserInfoProvider()
    }

    private fun setupUserInfoProvider() {
        val userInfoWriter = ScheduledWriter(
            NdkUserInfoDataWriter(
                storageDir,
                trackingConsentProvider,
                persistenceExecutorService,
                FileReaderWriter.create(internalLogger, localDataEncryption),
                FileMover(internalLogger),
                internalLogger,
                buildFilePersistenceConfig()
            ),
            persistenceExecutorService,
            internalLogger
        )
        userInfoProvider = DatadogUserInfoProvider(userInfoWriter)
    }

    private fun setupNetworkInfoProviders(appContext: Context) {
        val networkInfoWriter = ScheduledWriter(
            NdkNetworkInfoDataWriter(
                storageDir,
                trackingConsentProvider,
                persistenceExecutorService,
                FileReaderWriter.create(internalLogger, localDataEncryption),
                FileMover(internalLogger),
                internalLogger,
                buildFilePersistenceConfig()
            ),
            persistenceExecutorService,
            internalLogger
        )
        networkInfoProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CallbackNetworkInfoProvider(networkInfoWriter, internalLogger = internalLogger)
        } else {
            BroadcastReceiverNetworkInfoProvider(networkInfoWriter)
        }
        networkInfoProvider.register(appContext)
    }

    @Suppress("SpreadOperator")
    private fun setupOkHttpClient(configuration: Configuration.Core) {
        val connectionSpec = when {
            configuration.needsClearTextHttp -> ConnectionSpec.CLEARTEXT
            else -> ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .supportsTlsExtensions(true)
                .cipherSuites(*RESTRICTED_CIPHER_SUITES)
                .build()
        }

        val builder = OkHttpClient.Builder()
        builder.callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))

        if (BuildConfig.DEBUG) {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            builder.addNetworkInterceptor(CurlInterceptor())
        } else {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            builder.addInterceptor(GzipRequestInterceptor(internalLogger))
        }

        if (configuration.proxy != null) {
            builder.proxy(configuration.proxy)
            builder.proxyAuthenticator(configuration.proxyAuth)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        builder.dns(RotatingDnsResolver())

        okHttpClient = builder.build()
    }

    private fun setupExecutors() {
        @Suppress("UnsafeThirdPartyFunctionCall") // pool size can't be <= 0
        uploadExecutorService =
            LoggingScheduledThreadPoolExecutor(CORE_DEFAULT_POOL_SIZE, internalLogger)
        @Suppress("UnsafeThirdPartyFunctionCall") // workQueue can't be null
        persistenceExecutorService = LoggingThreadPoolExecutor(
            CORE_DEFAULT_POOL_SIZE,
            Runtime.getRuntime().availableProcessors(),
            THREAD_POOL_MAX_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque(),
            internalLogger
        )
    }

    private fun resolveProcessInfo(appContext: Context) {
        val currentProcessId = Process.myPid()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val currentProcess = manager?.runningAppProcesses?.firstOrNull {
            it.pid == currentProcessId
        }
        if (currentProcess == null) {
            isMainProcess = true
        } else {
            isMainProcess = appContext.packageName == currentProcess.processName
        }
    }

    private fun shutDownExecutors() {
        uploadExecutorService.shutdownNow()
        persistenceExecutorService.shutdownNow()

        try {
            uploadExecutorService.awaitTermination(1, TimeUnit.SECONDS)
            persistenceExecutorService.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            try {
                // Restore the interrupted status
                Thread.currentThread().interrupt()
            } catch (se: SecurityException) {
                // this should not happen
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Thread was unable to set its own interrupted state" },
                    se
                )
            }
        }
    }

    private fun cleanupApplicationInfo() {
        clientToken = ""
        packageName = ""
        packageVersionProvider = NoOpAppVersionProvider()
        serviceName = ""
        sourceName = DEFAULT_SOURCE_NAME
        sdkVersion = DEFAULT_SDK_VERSION
        isMainProcess = true
        envName = ""
        variant = ""
    }

    private fun cleanupProviders() {
        firstPartyHostHeaderTypeResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap())
        networkInfoProvider = NoOpNetworkInfoProvider()
        systemInfoProvider = NoOpSystemInfoProvider()
        timeProvider = NoOpTimeProvider()
        trackingConsentProvider = NoOpConsentProvider()
        userInfoProvider = NoOpMutableUserInfoProvider()
        androidInfoProvider = NoOpAndroidInfoProvider()
    }

    // endregion

    companion object {

        // region Constants

        internal val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
        private const val CORE_DEFAULT_POOL_SIZE = 1 // Only one thread will be kept alive
        internal const val DATADOG_STORAGE_DIR_NAME = "datadog-%s"

        // this is a default source to be used when uploading RUM/Logs/Span data, however there is a
        // possibility to override it which is useful when SDK is used via bridge, say
        // from React Native integration
        internal const val DEFAULT_SOURCE_NAME = "android"
        internal const val DEFAULT_SDK_VERSION = BuildConfig.SDK_VERSION_NAME
        internal const val DEFAULT_APP_VERSION = "?"

        internal val RESTRICTED_CIPHER_SUITES = arrayOf(
            // TLS 1.3

            // these 3 are mandatory to implement by TLS 1.3 RFC
            // https://datatracker.ietf.org/doc/html/rfc8446#section-9.1
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,

            // TLS 1.2

            // these 4 are FIPS 140-2 compliant by OpenSSL

            // GOV DC supports only that one and below
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,

            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,

            // these 4 are not listed in OpenSSL (because of CBC), but
            // claimed to be FIPS 140-2 compliant in other sources. Keep them for now, can be safely
            // dropped once min API is 21 (TODO RUMM-1594).
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256
        )

        const val DRAIN_WAIT_SECONDS = 10L
        const val NTP_CACHE_EXPIRATION_MINUTES = 30L
        const val NTP_DELAY_BETWEEN_SYNCS_MINUTES = 5L

        // TESTS ONLY, to prevent Kronos spinning sync threads in unit-tests, otherwise
        // LoggingSyncListener can interact with internalLogger, breaking mockito
        // verification expectations.
        // TODO RUMM-0000 isolate Kronos somehow for unit-tests
        internal var disableKronosBackgroundSync = false

        // endregion
    }
}
