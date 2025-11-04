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
import fr.xgouchet.elmyr.Forge
import java.util.UUID

object RumContextTestsUtils {
    const val RUM_CONTEXT_VIEW_ID = "view_id"
    const val RUM_CONTEXT_ACTION_ID = "action_id"
    const val RUM_CONTEXT_SESSION_ID = "session_id"
    const val RUM_CONTEXT_APPLICATION_ID = "application_id"

    private const val HEX = 16

    fun Forge.aRumContext(sessionId: Long? = null) = buildMap {
        put(RUM_CONTEXT_VIEW_ID, getForgery<UUID>().toString())
        put(RUM_CONTEXT_ACTION_ID, getForgery<UUID>().toString())
        put(RUM_CONTEXT_APPLICATION_ID, getForgery<UUID>().toString())
        sessionId?.let {
            put(
                RUM_CONTEXT_SESSION_ID,
                "aaaaaaaa-bbbb-Mccc-Nddd-${(sessionId).toULong().toString(HEX)}"
            )
        }
    }

    fun Forge.aDatadogContextWithRumContext(
        rumContext: Map<String, Any?>,
        accountInfo: AccountInfo? = null,
        userInfo: UserInfo = UserInfo()
    ): DatadogContext = getForgery<DatadogContext>().let {
        it.copy(
            featuresContext = it.featuresContext + mapOf(Feature.Companion.RUM_FEATURE_NAME to rumContext),
            accountInfo = accountInfo,
            userInfo = userInfo
        )
    }
}
