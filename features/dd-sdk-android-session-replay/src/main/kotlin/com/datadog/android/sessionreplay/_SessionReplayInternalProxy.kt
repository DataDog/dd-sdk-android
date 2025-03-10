/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.lint.InternalApi

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@InternalApi
@Suppress(
    "ClassName",
    "ClassNaming"
)
class _SessionReplayInternalProxy(private val builder: SessionReplayConfiguration.Builder) {
    /**
     * Sets an internal callback for session replay.
     *
     * @param internalCallback callback instance to override specific parts of the codebase.
     * @return [SessionReplayConfiguration.Builder] instance.
     */
    fun setInternalCallback(
        internalCallback: SessionReplayInternalCallback
    ): SessionReplayConfiguration.Builder {
        return builder.setInternalCallback(internalCallback)
    }
}
