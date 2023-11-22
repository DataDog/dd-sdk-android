/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import android.content.Context
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import fr.xgouchet.elmyr.Forge
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

/**
 * A stub implementation of [InternalSdkCore].
 *
 * It adds several functions to get info about internal state and usage:
 * [eventsWritten], …
 */
class StubSDKCore(
    private val forge: Forge,
    val mockContext: Context = mock(),
    private val mockSdkCore: InternalSdkCore = mock()
) : InternalSdkCore by mockSdkCore {

    private val featureScopes = mutableMapOf<String, StubFeatureScope>()

    private var datadogContext = forge.getForgery<DatadogContext>().copy(source = "android")

    init {
        whenever(mockContext.packageName) doReturn forge.anAlphabeticalString()
    }

    // region Stub

    /**
     * Lists all the events written by a given feature.
     * @param featureName the name of the feature
     * @return a list of pairs, each pair holds
     */
    fun eventsWritten(featureName: String): List<StubEvent> {
        return featureScopes[featureName]?.eventsWritten() ?: emptyList()
    }

    fun stubNetworkInfo(networkInfo: NetworkInfo) {
        datadogContext = datadogContext.copy(networkInfo = networkInfo)
    }

    fun stubUserInfo(userInfo: UserInfo) {
        networkInfo
        datadogContext = datadogContext.copy(userInfo = userInfo)
    }

    // endregion

    // region InternalSdkCore

    override val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver =
        StubFirstPartyHostHeaderTypeResolver()

    override fun getDatadogContext(): DatadogContext {
        return datadogContext
    }

    override val networkInfo: NetworkInfo
        get() = datadogContext.networkInfo

    // endregion

    // region FeatureSdkCore

    override val internalLogger: InternalLogger = StubInternalLogger()

    override fun registerFeature(feature: Feature) {
        featureScopes[feature.name] = StubFeatureScope(feature, { datadogContext })
        feature.onInitialize(mockContext)
        mockSdkCore.registerFeature(feature)
    }

    override fun getFeature(featureName: String): FeatureScope? {
        mockSdkCore.getFeature(featureName)
        return featureScopes[featureName]
    }

    override fun updateFeatureContext(
        featureName: String,
        updateCallback: (context: MutableMap<String, Any?>) -> Unit
    ) {
        val featureContext = datadogContext.featuresContext[featureName]?.toMutableMap() ?: mutableMapOf()
        updateCallback(featureContext)
        datadogContext = datadogContext.copy(
            featuresContext = datadogContext.featuresContext.toMutableMap().apply {
                put(featureName, featureContext)
            }
        )
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

    override fun setUserInfo(
        id: String?,
        name: String?,
        email: String?,
        extraInfo: Map<String, Any?>
    ) {
        datadogContext = datadogContext.copy(userInfo = UserInfo(id, name, email, extraInfo))
    }

    // endregion
}
