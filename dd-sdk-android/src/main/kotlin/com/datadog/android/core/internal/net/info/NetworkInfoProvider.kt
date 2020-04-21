/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import android.content.Context
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NetworkInfoProvider {
    fun register(context: Context)
    fun unregister(context: Context)
    fun getLatestNetworkInfo(): NetworkInfo
}
