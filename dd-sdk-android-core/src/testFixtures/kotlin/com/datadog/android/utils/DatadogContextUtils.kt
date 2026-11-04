/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import fr.xgouchet.elmyr.Forge

fun DatadogContext.withRumContext(rumContext: RumContext?): DatadogContext = copy(
    featuresContext = featuresContext + mapOf(
        Feature.RUM_FEATURE_NAME to if (rumContext == null) {
            emptyMap()
        } else {
            mapOf(FeatureContextKeys.RUM_CONTEXT to rumContext)
        }
    )
)

fun Forge.aDatadogContextWithRumContext(
    rumContext: RumContext?,
    accountInfo: AccountInfo? = null,
    userInfo: UserInfo = UserInfo()
): DatadogContext = getForgery<DatadogContext>()
    .copy(accountInfo = accountInfo, userInfo = userInfo)
    .withRumContext(rumContext)
