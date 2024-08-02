/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import com.datadog.android.core.internal.CoreFeature
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.UUID

internal open class ApplicationContextTestConfiguration<T : Context>(klass: Class<T>) :
    MockTestConfiguration<T>(klass) {

    lateinit var fakePackageName: String
    lateinit var fakeVersionName: String
    var fakeVersionCode: Int = 0
    lateinit var fakeVariant: String
    lateinit var fakeBuildId: String

    lateinit var fakePackageInfo: PackageInfo
    lateinit var fakeAppInfo: ApplicationInfo
    lateinit var mockPackageManager: PackageManager
    lateinit var mockAssetManager: AssetManager

    lateinit var fakeSandboxDir: File
    lateinit var fakeCacheDir: File
    lateinit var fakeFilesDir: File

    // region ApplicationContextTestConfiguration

    override fun setUp(forge: Forge) {
        super.setUp(forge)

        createFakeInfo(forge)
        mockPackageManager()
        mockAssetManager()

        whenever(mockInstance.applicationContext) doReturn mockInstance
        whenever(mockInstance.packageManager) doReturn mockPackageManager
        whenever(mockInstance.packageName) doReturn fakePackageName
        whenever(mockInstance.applicationInfo) doReturn fakeAppInfo
        whenever(mockInstance.assets) doReturn mockAssetManager

        // ???
        whenever(mockInstance.getSystemService(Context.ACTIVITY_SERVICE)) doReturn mock()
        whenever(mockInstance.getSharedPreferences(any(), any())) doReturn mock()

        // Filesystem
        fakeSandboxDir = Files.createTempDirectory("app-context").toFile()
        fakeCacheDir = File(fakeSandboxDir, "cache")
        fakeFilesDir = File(fakeSandboxDir, "files")
        whenever(mockInstance.cacheDir) doReturn fakeCacheDir
        whenever(mockInstance.filesDir) doReturn fakeFilesDir
    }

    override fun tearDown(forge: Forge) {
        super.tearDown(forge)
        fakeSandboxDir.deleteRecursively()
    }

    // endregion

    // region Internal

    private fun createFakeInfo(forge: Forge) {
        fakePackageName = forge.aStringMatching("[a-z]{2,4}(\\.[a-z]{3,8}){2,4}")
        fakeVersionName = forge.aStringMatching("[0-9](\\.[0-9]{1,3}){2,3}")
        fakeVersionCode = forge.anInt(1, 65536)
        fakeVariant = forge.anElementFrom(forge.anAlphabeticalString(), "")
        fakeBuildId = forge.getForgery<UUID>().toString()

        fakePackageInfo = PackageInfo()
        fakePackageInfo.packageName = fakePackageName
        fakePackageInfo.versionName = fakeVersionName
        @Suppress("DEPRECATION")
        fakePackageInfo.versionCode = fakeVersionCode
        fakePackageInfo.longVersionCode = fakeVersionCode.toLong()

        fakeAppInfo = ApplicationInfo()
    }

    private fun mockPackageManager() {
        mockPackageManager = mock()
        whenever(
            mockPackageManager.getPackageInfo(
                fakePackageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        ) doReturn fakePackageInfo
        whenever(mockPackageManager.getPackageInfo(fakePackageName, 0)) doReturn fakePackageInfo
    }

    private fun mockAssetManager() {
        mockAssetManager = mock()
        whenever(
            mockAssetManager.open(CoreFeature.BUILD_ID_FILE_NAME)
        ) doReturn fakeBuildId.byteInputStream()
    }

    // endregion
}
