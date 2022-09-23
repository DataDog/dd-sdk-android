/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.v2.api.Request
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext

internal class SessionReplayRequestFactory : RequestFactory {
    override fun create(
        context: DatadogContext,
        batchData: List<ByteArray>,
        batchMetadata: ByteArray?
    ): Request {
        // TODO: RUMM-2275
        return Request("", "", "", emptyMap(), ByteArray(0))
    }
}
