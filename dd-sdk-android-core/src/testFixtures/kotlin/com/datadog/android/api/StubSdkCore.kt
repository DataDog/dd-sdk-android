/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import android.content.Context
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import fr.xgouchet.elmyr.Forge
import org.mockito.Mockito.mock

/**
 * A stub implementation of [InternalSdkCore].
 *
 * It adds several functions to get info about internal state and usage:
 * [eventsWritten], …
 */
class StubSdkCore(
    private val forge: Forge,
    private val mockSdkCore: InternalSdkCore = mock(),
    private val mockContext: Context = mock()
) : InternalSdkCore by mockSdkCore {

    private val featureScopes = mutableMapOf<String, StubFeatureScope>()
    private val datadogContext = forge.getForgery<DatadogContext>().copy(source = "android")

    // region Stub

    /**
     * Lists all the events written by a given feature.
     * @param featureName the name of the feature
     * @return a list of pairs, each pair holds
     */
    fun eventsWritten(featureName: String): List<StubEvent> {
        return featureScopes[featureName]?.eventsWritten() ?: emptyList()
    }

    // endregion

    // region InternalSdkCore

    override val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver = ApproveAllFirstPartyHostHeaderTypeResolver()

    // endregion

    // region FeatureSdkCore

    override val internalLogger: InternalLogger = StubInternalLogger()

    override fun registerFeature(feature: Feature) {
        featureScopes[feature.name] = StubFeatureScope(feature, datadogContext)
        feature.onInitialize(mockContext)
        mockSdkCore.registerFeature(feature)
    }

    override fun getFeature(featureName: String): FeatureScope? {
        mockSdkCore.getFeature(featureName)
        return featureScopes[featureName]
    }

    // endregion

    // region SdkCore

    override val time: TimeInfo
        get() {
            val nanos = System.nanoTime()
            return TimeInfo(
                deviceTimeNs = nanos,
                serverTimeNs = nanos,
                serverTimeOffsetMs = 0L,
                serverTimeOffsetNs = 0L
            )
        }

    // endregion
}
