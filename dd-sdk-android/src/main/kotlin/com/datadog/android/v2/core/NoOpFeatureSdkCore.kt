/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import java.util.concurrent.TimeUnit

/**
 * A no-op implementation of [SdkCore].
 */
internal class NoOpFeatureSdkCore : FeatureSdkCore {

    override val name: String = "no-op"

    override val time: TimeInfo = with(System.currentTimeMillis()) {
        TimeInfo(
            deviceTimeNs = TimeUnit.MILLISECONDS.toNanos(this),
            serverTimeNs = TimeUnit.MILLISECONDS.toNanos(this),
            serverTimeOffsetNs = 0L,
            serverTimeOffsetMs = 0L
        )
    }

    override val service: String
        get() = ""

    override val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver
        get() = DefaultFirstPartyHostHeaderTypeResolver(emptyMap())

    override val internalLogger: InternalLogger
        get() = SdkInternalLogger(this)

    override fun registerFeature(feature: Feature) = Unit

    override fun getFeature(featureName: String): FeatureScope? = null

    override fun setTrackingConsent(consent: TrackingConsent) = Unit

    override fun setUserInfo(userInfo: UserInfo) = Unit

    override fun addUserProperties(extraInfo: Map<String, Any?>) = Unit

    override fun clearAllData() = Unit

    override fun updateFeatureContext(
        featureName: String,
        updateCallback: (MutableMap<String, Any?>) -> Unit
    ) = Unit

    override fun getFeatureContext(featureName: String): Map<String, Any?> = emptyMap()

    override fun setEventReceiver(featureName: String, `receiver`: FeatureEventReceiver) = Unit

    override fun removeEventReceiver(featureName: String) = Unit
}
