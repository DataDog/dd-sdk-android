/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.rum.TTIDEvent
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver {

    private var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    override val requestFactory: RequestFactory = ProfilingRequestFactory(
        configuration.customEndpointUrl,
        sdkCore.internalLogger
    )

    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.Companion.DEFAULT

    override val name: String
        get() = Feature.Companion.PROFILING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        profiler.apply {
            this.internalLogger = sdkCore.internalLogger
            this.onProfilingSuccess = { result ->
                dataWriter.write(profilingResult = result)
            }
        }
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        setProfilingFlag(appContext)
        sdkCore.setEventReceiver(name, this)
        dataWriter = createDataWriter(sdkCore)
    }

    override fun onStop() {
        profiler.stop()
        sdkCore.removeEventReceiver(name)
    }

    override fun onReceive(event: Any) {
        if (event !is TTIDEvent) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
            return
        }
        profiler.stop()
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { "Profiling stopped with TTID=${event.value}" }
        )
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): ProfilingDataWriter {
        return ProfilingDataWriter(sdkCore)
    }

    companion object {
        private const val UNSUPPORTED_EVENT_TYPE =
            "Profiling feature receive an event of unsupported type=%s."
    }
}
