/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import dagger.Lazy
import javax.inject.Inject

internal interface RickAndMortyFallbackListener {
    /** Called once, when the first Rick & Morty request is served by the local server. */
    fun onFallbackActivated()

    /** Called when the local server can't be started or the local request fails. */
    fun onFallbackFailed(throwable: Throwable)
}

/**
 * Marks the RUM session with [FALLBACK_ATTRIBUTE] and reports fallback events through the SDK logger.
 * In the Timeseries scenario RUM is enabled in both runs; otherwise the RUM monitor is a no-op.
 */
internal class RumRickAndMortyFallbackListener(
    private val sdkCore: Lazy<SdkCore>,
    private val rumMonitorProvider: (SdkCore) -> RumMonitor
) : RickAndMortyFallbackListener {

    @Inject
    constructor(sdkCore: Lazy<SdkCore>) : this(sdkCore, { GlobalRumMonitor.get(it) })

    override fun onFallbackActivated() {
        val core = sdkCore.get()
        rumMonitorProvider(core).addAttribute(FALLBACK_ATTRIBUTE, true)
        core.internalLogger().log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { FALLBACK_ACTIVATED_MESSAGE },
            onlyOnce = true
        )
    }

    override fun onFallbackFailed(throwable: Throwable) {
        sdkCore.get().internalLogger().log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { FALLBACK_FAILED_MESSAGE },
            throwable = throwable,
            onlyOnce = true
        )
    }

    private fun SdkCore.internalLogger(): InternalLogger {
        return (this as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
    }

    internal companion object {
        const val FALLBACK_ATTRIBUTE = "benchmark.rick_and_morty_fallback"
        const val FALLBACK_ACTIVATED_MESSAGE =
            "Rick & Morty API fallback activated: requests are served by the local snapshot server."
        const val FALLBACK_FAILED_MESSAGE =
            "Rick & Morty API fallback failed: the local snapshot server is unavailable."
    }
}
