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
import java.io.File

/**
 * Mocks T:Context with the minimal behavior to initialize the Datadog library.
 */
inline fun <reified T : Context> mockContext(
    packageName: String = BuildConfig.LIBRARY_PACKAGE_NAME,
    versionName: String? = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE
): T {
    val mockPackageInfo = PackageInfo()
    val mockPackageMgr = mock<PackageManager>()
    val mockContext = mock<T>()

    mockPackageInfo.versionName = versionName
    mockPackageInfo.versionCode = versionCode
    whenever(mockPackageMgr.getPackageInfo(packageName, 0)) doReturn mockPackageInfo

    whenever(mockContext.applicationContext) doReturn mockContext
    whenever(mockContext.packageManager) doReturn mockPackageMgr
    whenever(mockContext.applicationInfo) doReturn mock()
    whenever(mockContext.packageName) doReturn packageName
    whenever(mockContext.filesDir) doReturn File("/dev/null")

    return mockContext
}
