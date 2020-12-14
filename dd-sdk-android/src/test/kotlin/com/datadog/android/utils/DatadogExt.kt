/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.io.File
import java.util.UUID
import kotlin.math.min

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
    whenever(mockContext.getSystemService(Context.ACTIVITY_SERVICE)) doReturn mock()
    whenever(mockContext.getSharedPreferences(any(), any())) doReturn mock()

    whenever(mockContext.applicationContext) doReturn mockContext
    whenever(mockContext.packageManager) doReturn mockPackageMgr
    val mockApplicationInfo = mock<ApplicationInfo>()
    whenever(mockContext.applicationInfo) doReturn mockApplicationInfo
    if (BuildConfig.DEBUG) {
        mockApplicationInfo.flags =
            ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_ALLOW_BACKUP
    }
    whenever(mockContext.packageName) doReturn packageName
    whenever(mockContext.filesDir) doReturn File("/dev/null")

    return mockContext
}

/**
 * Resolves the expected tag name for the logcat message depending on the application type
 * (debug or release)
 */
fun resolveTagName(caller: Any, defaultIfNotDebug: String? = null): String {
    val tag = if (Datadog.isDebug) {
        val javaClass = caller.javaClass
        val strippedName = javaClass.simpleName.replace(Regex("(\\$\\d+)+$"), "")
        if (javaClass.isAnonymousClass) {
            javaClass.enclosingClass!!.simpleName.replace(Regex("(\\$\\d+)+$"), "")
        } else if (javaClass.isLocalClass) {
            javaClass.enclosingClass!!.simpleName + "$" + strippedName
        } else {
            strippedName
        }
    } else {
        defaultIfNotDebug ?: "Datadog"
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        tag
    } else {
        tag.substring(0, min(tag.length, 23))
    }
}

internal fun mockSdkLogHandler(mockHandler: LogHandler): LogHandler {
    val originalHandler = sdkLogger.getFieldValue<LogHandler, Logger>("handler")
    sdkLogger.setFieldValue("handler", mockHandler)
    return originalHandler
}

internal fun restoreSdkLogHandler(originalHandler: LogHandler) {
    sdkLogger.setFieldValue("handler", originalHandler)
}

internal fun mockDevLogHandler(): LogHandler {
    val mockHandler: LogHandler = mock()

    devLogger.setFieldValue("handler", mockHandler)

    return mockHandler
}

internal fun mockCoreFeature(
    packageName: String = BuildConfig.LIBRARY_PACKAGE_NAME,
    packageVersion: String = BuildConfig.VERSION_NAME,
    envName: String = BuildConfig.BUILD_TYPE,
    variant: String = BuildConfig.VERSION_NAME + BuildConfig.BUILD_TYPE,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING,
    rumApplicationId: String = UUID.randomUUID().toString()
) {
    CoreFeature.isMainProcess = true
    CoreFeature.envName = envName
    CoreFeature.serviceName = packageName
    CoreFeature.packageName = packageName
    CoreFeature.packageVersion = packageVersion
    CoreFeature.variant = variant
    CoreFeature.rumApplicationId = rumApplicationId
    CoreFeature.persistenceExecutorService = mock()
    CoreFeature.uploadExecutorService = mock()
    CoreFeature.timeProvider = mock()
    CoreFeature.networkInfoProvider = mock()
    CoreFeature.trackingConsentProvider = mock()
    CoreFeature.userInfoProvider = mock()
    whenever(CoreFeature.trackingConsentProvider.getConsent()) doReturn trackingConsent
}
