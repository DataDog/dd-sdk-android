/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import java.util.concurrent.atomic.AtomicReference

internal class DefaultAppVersionProvider(
    initialVersion: String,
    override val versionCode: String
) : AppVersionProvider {

    private val value = AtomicReference(initialVersion)

    override var version: String
        get() = value.get()
        set(value) {
            this.value.set(value)
        }
}
