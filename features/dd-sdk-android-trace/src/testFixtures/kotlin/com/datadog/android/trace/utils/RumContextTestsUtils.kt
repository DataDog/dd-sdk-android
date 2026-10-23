/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.utils

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import fr.xgouchet.elmyr.Forge
import java.util.UUID

object RumContextTestsUtils {
    private const val HEX = 16

    fun Forge.aRumContext(sessionId: Long = aLong()) = RumContext(
        viewId = getForgery<UUID>().toString(),
        actionId = getForgery<UUID>().toString(),
        applicationId = getForgery<UUID>().toString(),
        sessionSampleRate = aFloat(min = 0f, max = 100f),
        sessionId = "aaaaaaaa-bbbb-Mccc-Nddd-${sessionId.toULong().toString(HEX)}"
    )

    fun Forge.aDatadogContextWithRumContext(
        rumContext: RumContext?,
        accountInfo: AccountInfo? = null,
        userInfo: UserInfo = UserInfo()
    ): DatadogContext = getForgery<DatadogContext>().let {
        it.copy(
            featuresContext =
            it.featuresContext +
                mapOf(Feature.RUM_FEATURE_NAME to mapOf(FeatureContextKeys.RUM_CONTEXT to rumContext)),
            accountInfo = accountInfo,
            userInfo = userInfo
        )
    }
}
