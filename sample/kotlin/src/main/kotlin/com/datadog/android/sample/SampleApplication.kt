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
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.event.EventMapper
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.ndk.NdkCrashReports
import com.datadog.android.okhttp.DatadogEventListener
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.event.ViewEventMapper
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
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.android.timber.DatadogTree
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
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

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor(traceSampler = RateBasedSampler(100f)))
        .addNetworkInterceptor(TracingInterceptor(traceSampler = RateBasedSampler(100f)))
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()

    private val retrofitClient = Retrofit.Builder()
        .baseUrl("https://api.datadoghq.com/api/v2/")
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .addCallAdapterFactory(RxJava3CallAdapterFactory.createSynchronous())
        .client(okHttpClient)
        .build()

    private val retrofitBaseDataSource = retrofitClient.create(RemoteDataSource::class.java)

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

        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            preferences.getTrackingConsent()
        )

        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig)

        val sessionReplayConfig = SessionReplayConfiguration.Builder(SAMPLE_IN_ALL_SESSIONS)
            .apply {
                if (BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_SESSION_REPLAY_URL)
                }
            }
            .addExtensionSupport(MaterialExtensionSupport())
            .build()
        SessionReplay.enable(sessionReplayConfig)

        val logsConfig = LogsConfiguration.Builder().apply {
            if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
                useCustomEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
            }
        }.build()
        Logs.enable(logsConfig)

        val tracesConfig = TraceConfiguration.Builder().apply {
            if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
                useCustomEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
            }
        }.build()
        Trace.enable(tracesConfig)

        NdkCrashReports.enable()

        Datadog.setUserInfo(
            id = preferences.getUserId(),
            name = preferences.getUserName(),
            email = preferences.getUserEmail(),
            extraInfo = mapOf(
                UserFragment.GENDER_KEY to preferences.getUserGender(),
                UserFragment.AGE_KEY to preferences.getUserAge()
            )
        )

        GlobalTracer.registerIfAbsent(
            AndroidTracer.Builder()
                .setService(BuildConfig.APPLICATION_ID)
                .build()
        )
        GlobalRumMonitor.get().debug = true
        TracingRxJava3Utils.enableTracing(GlobalTracer.get())
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .apply {
                if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
                }
            }
            .useViewTrackingStrategy(
                NavigationViewTrackingStrategy(
                    R.id.nav_host_fragment,
                    true,
                    SampleNavigationPredicate()
                )
            )
            .setTelemetrySampleRate(100f)
            .trackUserInteractions()
            .trackLongTasks(250L)
            .setViewEventMapper(object : ViewEventMapper {
                override fun map(event: ViewEvent): ViewEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setActionEventMapper(object : EventMapper<ActionEvent> {
                override fun map(event: ActionEvent): ActionEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setResourceEventMapper(object : EventMapper<ResourceEvent> {
                override fun map(event: ResourceEvent): ResourceEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setErrorEventMapper(object : EventMapper<ErrorEvent> {
                override fun map(event: ErrorEvent): ErrorEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .setLongTaskEventMapper(object : EventMapper<LongTaskEvent> {
                override fun map(event: LongTaskEvent): LongTaskEvent {
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    return event
                }
            })
            .build()
    }

    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR
        )
            .setFirstPartyHosts(tracedHosts)

        try {
            configBuilder.useSite(DatadogSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        return configBuilder.build()
    }

    @Suppress("TooGenericExceptionCaught", "CheckInternal")
    private fun initializeTimber() {
        val logger = Logger.Builder()
            .setName("timber")
            .setNetworkInfoEnabled(true)
            .build()

        val device = JsonObject()
        val abis = JsonArray()
        try {
            device.addProperty("api", Build.VERSION.SDK_INT)
            device.addProperty("brand", Build.BRAND)
            device.addProperty("manufacturer", Build.MANUFACTURER)
            device.addProperty("model", Build.MODEL)
            for (abi in Build.SUPPORTED_ABIS) {
                abis.add(abi)
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
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
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
