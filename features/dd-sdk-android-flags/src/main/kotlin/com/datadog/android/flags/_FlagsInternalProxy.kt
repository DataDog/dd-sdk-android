/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.lint.InternalApi

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@InternalApi
@Suppress("ClassName", "UndocumentedPublicFunction")
class _FlagsInternalProxy(private val client: FlagsClient) {
    fun getFlagAssignmentsSnapshot(): Map<String, UnparsedFlag> = if (client is DatadogFlagsClient) {
        client.getFlagAssignmentsSnapshot()
    } else {
        emptyMap()
    }

    fun trackFlagSnapshotEvaluation(flagKey: String, flag: UnparsedFlag, context: EvaluationContext) {
        if (client is DatadogFlagsClient) {
            client.trackFlagSnapshotEvaluation(flagKey, flag, context)
        }
    }
}
