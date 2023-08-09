/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.util.Base64
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.recorder.wrappers.Base64Wrapper

internal class Base64Utils(
    private val base64Wrapper: Base64Wrapper = Base64Wrapper()
) {
    @WorkerThread
    internal fun serializeToBase64String(byteArray: ByteArray): String =
        base64Wrapper.encodeToString(byteArray, Base64.DEFAULT)
}
