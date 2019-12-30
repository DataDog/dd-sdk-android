/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.datadog.android.BuildConfig
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever

/**
 * Mocks a context with the minimal behavior to initialize the Datadog library.
 */
fun mockContext(
    fakePackageName: String = BuildConfig.LIBRARY_PACKAGE_NAME,
    fakePackageVersion: String = BuildConfig.VERSION_NAME
): Context {
    val mockPackageInfo = PackageInfo()
    val mockPackageMgr = mock<PackageManager>()
    val mockContext = mock<Context>()

    mockPackageInfo.versionName = fakePackageVersion
    whenever(mockPackageMgr.getPackageInfo(fakePackageName, 0)) doReturn mockPackageInfo

    whenever(mockContext.applicationContext) doReturn mockContext
    whenever(mockContext.packageManager) doReturn mockPackageMgr
    whenever(mockContext.packageName) doReturn fakePackageName

    return mockContext
}
