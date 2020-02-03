/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.timber

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.log.Logger
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.SystemStreamExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import timber.log.Timber

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemStreamExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogTreeTest {

    lateinit var testedTree: Timber.Tree

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakePackageName: String
    lateinit var fakeMessage: String
    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString(size = 10)
        fakeLoggerName = forge.anAlphabeticalString()
        fakePackageName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()

        val mockPackageInfo = PackageInfo()
        val mockPackageMgr = mock<PackageManager>()
        val mockContext: Context = mock()
        val mockApplicationInfo: ApplicationInfo = mock()
        mockPackageInfo.versionName = forge.anAlphabeticalString()
        whenever(mockPackageMgr.getPackageInfo(fakePackageName, 0)) doReturn mockPackageInfo
        whenever(mockContext.filesDir).thenReturn(tempDir)
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.packageManager) doReturn mockPackageMgr
        whenever(mockContext.packageName) doReturn fakePackageName
        whenever(mockContext.applicationInfo) doReturn mockApplicationInfo
        if (BuildConfig.DEBUG) {
            mockApplicationInfo.flags =
                ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_ALLOW_BACKUP
        }
        val config = DatadogConfig.Builder(forge.anHexadecimalString())
            .build()
        Datadog.initialize(mockContext, config)

        val builder = Logger.Builder()
            .setServiceName(fakeServiceName)
            .setLoggerName(fakeLoggerName)
            .setLogcatLogsEnabled(true)
            .setDatadogLogsEnabled(false)
            .setNetworkInfoEnabled(false)
        val logger = builder.build()

        testedTree = DatadogTree(logger)
        Timber.plant(testedTree)
    }

    @AfterEach
    fun `set up`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `tree logs message with verbose level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.v(fakeMessage)

        verifyLogSideEffects(Log.VERBOSE, outputStream)
    }

    @Test
    fun `tree logs message with debug level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.d(fakeMessage)

        verifyLogSideEffects(Log.DEBUG, outputStream)
    }

    @Test
    fun `tree logs message with info level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.i(fakeMessage)

        verifyLogSideEffects(Log.INFO, outputStream)
    }

    @Test
    fun `tree logs message with warning level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.w(fakeMessage)

        verifyLogSideEffects(Log.WARN, outputStream)
    }

    @Test
    fun `tree logs message with error level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.e(fakeMessage)

        verifyLogSideEffects(Log.ERROR, outputStream)
    }

    @Test
    fun `tree logs message with assert level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.wtf(fakeMessage)

        verifyLogSideEffects(Log.ASSERT, outputStream)
    }

    // region Internal

    private fun verifyLogSideEffects(
        logLevel: Int,
        outputStream: ByteArrayOutputStream
    ) {
        val tag = if (BuildConfig.DEBUG) "DatadogTree" else fakeServiceName
        assertThat(outputStream)
            .hasLogLine(logLevel, tag, fakeMessage)
    }

    // endregion
}
