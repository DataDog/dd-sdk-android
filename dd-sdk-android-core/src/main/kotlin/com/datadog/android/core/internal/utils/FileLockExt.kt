/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import java.io.IOException
import java.nio.channels.FileLock

@Throws(IOException::class)
@Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
internal inline fun <reified R> FileLock.use(block: (FileLock) -> R): R {
    try {
        return block(this)
    } finally {
        this.release()
    }
}
