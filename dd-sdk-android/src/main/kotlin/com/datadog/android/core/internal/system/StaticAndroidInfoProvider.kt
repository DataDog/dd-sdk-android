/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.os.Build

internal object StaticAndroidInfoProvider : AndroidInfoProvider {
    override fun getDeviceModel(): String = Build.MODEL

    override fun getDeviceBuildId(): String = Build.ID

    override fun getDeviceVersion(): String = Build.VERSION.RELEASE
}
