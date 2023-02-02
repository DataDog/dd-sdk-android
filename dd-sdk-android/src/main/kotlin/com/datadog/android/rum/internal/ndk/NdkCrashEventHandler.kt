/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.storage.DataWriter

internal interface NdkCrashEventHandler {
    fun handleEvent(event: Map<*, *>, sdkCore: SdkCore, rumWriter: DataWriter<Any>)
}
