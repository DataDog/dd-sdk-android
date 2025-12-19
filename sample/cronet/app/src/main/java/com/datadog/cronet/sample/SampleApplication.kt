package com.datadog.cronet.sample

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import org.chromium.net.CronetEngine

class SampleApplication : Application() {
    internal lateinit var cronetEngine: CronetEngine

    @OptIn(ExperimentalRumApi::class)
    override fun onCreate() {
        super.onCreate()
        val tracedHosts = listOf("storage.googleapis.com")
        Datadog.initialize(
            context = this,
            trackingConsent = TrackingConsent.GRANTED,
            configuration = Configuration
                .Builder(
                    clientToken = BuildConfig.DD_API_TOKEN,
                    env = "env",
                )
                .setFirstPartyHosts(tracedHosts)
                .setBatchSize(BatchSize.SMALL)
                .setUploadFrequency(UploadFrequency.FREQUENT)
                .build()
        )

        val rumConfig = RumConfiguration.Builder(BuildConfig.DD_APP_ID)
            .collectAccessibility(true)
            .trackBackgroundEvents(true)
            .build()

        Rum.enable(rumConfig)


        val traceConfig = TraceConfiguration.Builder()
            .build()

        Trace.enable(traceConfig)

        cronetEngine = CronetEngine.Builder(this)
            .build()
    }
}
