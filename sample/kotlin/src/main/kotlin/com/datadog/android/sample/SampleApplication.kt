/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.Datadog
import com.datadog.android.Datadog.setUserInfo
import com.datadog.android.DatadogEventListener
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.event.EventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.log.Logger
import com.datadog.android.ndk.NdkCrashReportsPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumInterceptor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.sample.data.db.LocalDataSource
import com.datadog.android.sample.data.remote.RemoteDataSource
import com.datadog.android.sample.picture.CoilImageLoader
import com.datadog.android.sample.picture.FrescoImageLoader
import com.datadog.android.sample.picture.PicassoImageLoader
import com.datadog.android.sample.user.UserFragment
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayFeature
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.android.timber.DatadogTree
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.tracing.TracingInterceptor
import com.facebook.stetho.Stetho
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.opentracing.rxjava3.TracingRxJava3Utils
import io.opentracing.util.GlobalTracer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

/**
 * The main [Application] for the sample project.
 */
@Suppress("MagicNumber")
class SampleApplication : Application() {

    private val tracedHosts = listOf(
        "datadoghq.com",
        "127.0.0.1"
    )

    private val webViewTrackingHosts = listOf(
        "datadoghq.dev"
    )

    // TODO RUMM-0000 lazy is needed here, because without it global first party host detector is
    //  not available yet at the interceptor construction time
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RumInterceptor(traceSamplingRate = 100f))
            .addNetworkInterceptor(TracingInterceptor(traceSamplingRate = 100f))
            .eventListenerFactory(DatadogEventListener.Factory())
            .build()
    }

    private val retrofitClient by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.datadoghq.com/api/v2/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.createSynchronous())
            .client(okHttpClient)
            .build()
    }

    private val retrofitBaseDataSource by lazy { retrofitClient.create(RemoteDataSource::class.java) }

    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
        initializeDatadog()

        initializeTimber()

        initializeImageLoaders()
    }

    private fun initializeImageLoaders() {
        CoilImageLoader.initialize(this, okHttpClient)
        PicassoImageLoader.initialize(this, okHttpClient)
        FrescoImageLoader.initialize(this, okHttpClient)
    }

    private fun initializeDatadog() {
        val preferences = Preferences.defaultPreferences(this)

        Datadog.initialize(
            this,
            createDatadogCredentials(),
            createDatadogConfiguration(),
            preferences.getTrackingConsent()
        )
        Datadog.setVerbosity(Log.VERBOSE)

        val sessionReplayConfig = SessionReplayConfiguration.Builder()
            .apply {
                if (BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL)
                }
            }
            .setPrivacy(SessionReplayPrivacy.ALLOW_ALL)
            .addExtensionSupport(MaterialExtensionSupport())
            .build()
        val sessionReplayFeature = SessionReplayFeature(sessionReplayConfig)
        Datadog.registerFeature(sessionReplayFeature)

        Datadog.enableRumDebugging(true)
        setUserInfo(
            preferences.getUserId(),
            preferences.getUserName(),
            preferences.getUserEmail(),
            mapOf(
                UserFragment.GENDER_KEY to preferences.getUserGender(),
                UserFragment.AGE_KEY to preferences.getUserAge()
            )
        )

        GlobalTracer.registerIfAbsent(
            AndroidTracer.Builder()
                .setServiceName(BuildConfig.APPLICATION_ID)
                .build()
        )
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        TracingRxJava3Utils.enableTracing(GlobalTracer.get())
    }

    private fun createDatadogCredentials(): Credentials {
        return Credentials(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            envName = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR,
            rumApplicationId = BuildConfig.DD_RUM_APPLICATION_ID
        )
    }

    private fun createDatadogConfiguration(): Configuration {
        @Suppress("DEPRECATION")
        val configBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .sampleTelemetry(100f)
            .setFirstPartyHosts(tracedHosts)
            .addPlugin(NdkCrashReportsPlugin(), Feature.CRASH)
            .setWebViewTrackingHosts(webViewTrackingHosts)
            .useViewTrackingStrategy(
                NavigationViewTrackingStrategy(
                    R.id.nav_host_fragment,
                    true,
                    SampleNavigationPredicate()
                )
            )
            .trackInteractions()
            .trackLongTasks(250L)

        configBuilder
            .setRumViewEventMapper(object : ViewEventMapper {
                override fun map(event: ViewEvent): ViewEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setRumActionEventMapper(object : EventMapper<ActionEvent> {
                override fun map(event: ActionEvent): ActionEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setRumResourceEventMapper(object : EventMapper<ResourceEvent> {
                override fun map(event: ResourceEvent): ResourceEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setRumErrorEventMapper(object : EventMapper<ErrorEvent> {
                override fun map(event: ErrorEvent): ErrorEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setRumLongTaskEventMapper(object : EventMapper<LongTaskEvent> {
                override fun map(event: LongTaskEvent): LongTaskEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })

        try {
            configBuilder.useSite(DatadogSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
            configBuilder.useCustomLogsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
            configBuilder.useCustomCrashReportsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
        }
        if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
            configBuilder.useCustomTracesEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
        }
        if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
            configBuilder.useCustomRumEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
        }
        return configBuilder.build()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun initializeTimber() {
        val logger = Logger.Builder()
            .setLoggerName("timber")
            .setNetworkInfoEnabled(true)
            .build()

        val device = JsonObject()
        val abis = JsonArray()
        try {
            device.addProperty("api", Build.VERSION.SDK_INT)
            device.addProperty("brand", Build.BRAND)
            device.addProperty("manufacturer", Build.MANUFACTURER)
            device.addProperty("model", Build.MODEL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (abi in Build.SUPPORTED_ABIS) {
                    abis.add(abi)
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error setting device and abi properties")
        }
        logger.addAttribute("device", device)
        logger.addAttribute("supported_abis", abis)

        logger.addTag("flavor", BuildConfig.FLAVOR)
        logger.addTag("build_type", BuildConfig.BUILD_TYPE)

        Timber.plant(DatadogTree(logger))
    }

    companion object {
        init {
            System.loadLibrary("datadog-native-sample-lib")
        }

        internal const val ATTR_IS_MAPPED = "is_mapped"

        internal fun getViewModelFactory(context: Context): ViewModelProvider.Factory {
            return ViewModelFactory(
                getOkHttpClient(context),
                getRemoteDataSource(context),
                LocalDataSource(context)
            )
        }

        internal fun getOkHttpClient(context: Context): OkHttpClient {
            val application = context.applicationContext as SampleApplication
            return application.okHttpClient
        }

        private fun getRemoteDataSource(context: Context): RemoteDataSource {
            val application = context.applicationContext as SampleApplication
            return application.retrofitBaseDataSource
        }
    }
}
