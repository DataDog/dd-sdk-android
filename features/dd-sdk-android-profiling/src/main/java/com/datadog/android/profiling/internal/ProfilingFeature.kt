/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.ProfilingConfiguration

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    configuration: ProfilingConfiguration
) : StorageBackedFeature, FeatureEventReceiver {

    private var perfettoProfiler: Profiler = NoOpProfiler()

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
        perfettoProfiler = PerfettoProfiler(
            internalLogger = sdkCore.internalLogger,
            timeProvider = DefaultTimeProvider(),
            profilingExecutor = sdkCore.createSingleThreadExecutorService(
                PROFILING_EXECUTOR_SERVICE_NAME
            ),
            onProfilingSuccess = { result ->
                dataWriter.write(profilingResult = result)
            }
        ).also {
            // Currently we start profiling right away, in the future we might want to
            // start it in earlier stage.
            it.start(appContext)
        }
        sdkCore.setEventReceiver(name, this)
        dataWriter = createDataWriter(sdkCore)
    }

    override fun onStop() {
        perfettoProfiler.stop()
        sdkCore.removeEventReceiver(name)
    }

    override fun onReceive(event: Any) {
        // TODO RUM-11887: Receive TTID event and stop profiling.
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): ProfilingDataWriter {
        return ProfilingDataWriter(sdkCore)
    }

    companion object {
        private const val PROFILING_EXECUTOR_SERVICE_NAME = "profiling"
    }
}
