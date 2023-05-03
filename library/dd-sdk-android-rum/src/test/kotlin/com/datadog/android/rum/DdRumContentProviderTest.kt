/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Process
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.net.URI

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DdRumContentProviderTest {

    lateinit var testedProvider: ContentProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockActivityManager: ActivityManager

    lateinit var fakeCurrentProcessInfo: ActivityManager.RunningAppProcessInfo
    lateinit var fakeOtherProcessInfo: ActivityManager.RunningAppProcessInfo

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeCurrentProcessInfo = ActivityManager.RunningAppProcessInfo().apply {
            this.processName = forge.anAlphabeticalString()
            this.pid = Process.myPid()
            this.importance = forge.anInt()
        }
        fakeOtherProcessInfo = ActivityManager.RunningAppProcessInfo().apply {
            this.processName = forge.anAlphabeticalString()
            this.pid = Process.myPid() + forge.aSmallInt()
            this.importance = forge.anInt()
        }

        whenever(mockContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        whenever(mockActivityManager.runningAppProcesses).thenReturn(
            listOf(fakeCurrentProcessInfo, fakeOtherProcessInfo)
        )

        testedProvider = DdRumContentProvider()
        testedProvider.setFieldValue("mContext", mockContext)
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = 0
    }

    // region onCreate

    @Test
    fun `𝕄 detect process importance 𝕎 onCreate()`(
        @IntForgery processImportance: Int
    ) {
        // Given
        fakeCurrentProcessInfo.importance = processImportance

        // When
        testedProvider.onCreate()

        // Then
        assertThat(DdRumContentProvider.processImportance).isEqualTo(processImportance)
    }

    @Test
    fun `𝕄 detect process importance once 𝕎 onCreate() twice`(
        @IntForgery processImportance1: Int,
        @IntForgery processImportance2: Int
    ) {
        // Given
        assumeTrue(processImportance1 != processImportance2)

        // When
        fakeCurrentProcessInfo.importance = processImportance1
        testedProvider.onCreate()
        fakeCurrentProcessInfo.importance = processImportance2
        testedProvider.onCreate()

        // Then
        assertThat(DdRumContentProvider.processImportance).isEqualTo(processImportance1)
    }

    @Test
    fun `𝕄 detect default process importance 𝕎 onCreate() {no context}`() {
        // Given
        testedProvider.setFieldValue("mContext", null as Context?)

        // When
        testedProvider.onCreate()

        // Then
        assertThat(DdRumContentProvider.processImportance)
            .isEqualTo(DdRumContentProvider.DEFAULT_IMPORTANCE)
    }

    @Test
    fun `𝕄 detect default process importance 𝕎 onCreate() {no activity mgr}`() {
        // Given
        whenever(mockContext.getSystemService(Context.ACTIVITY_SERVICE)) doReturn null

        // When
        testedProvider.onCreate()

        // Then
        assertThat(DdRumContentProvider.processImportance)
            .isEqualTo(DdRumContentProvider.DEFAULT_IMPORTANCE)
    }

    // endregion

    // region ContentProvider

    @Test
    fun `𝕄 return null 𝕎 query()`(
        @Forgery uri: URI,
        @StringForgery projection: List<String>,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>,
        @StringForgery sortOrder: String
    ) {
        // When
        val cursor = testedProvider.query(
            Uri.parse(uri.toString()),
            projection.toTypedArray(),
            selection,
            selectionArgs.toTypedArray(),
            sortOrder
        )

        // Then
        assertThat(cursor).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 getType()`(
        @Forgery uri: URI
    ) {
        // When
        val type = testedProvider.getType(Uri.parse(uri.toString()))

        // Then
        assertThat(type).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 insert()`(
        @Forgery uri: URI
    ) {
        // When
        val type = testedProvider.insert(
            Uri.parse(uri.toString()),
            ContentValues()
        )

        // Then
        assertThat(type).isNull()
    }

    @Test
    fun `𝕄 return 0 𝕎 delete()`(
        @Forgery uri: URI,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>
    ) {
        // When
        val deleted = testedProvider.delete(
            Uri.parse(uri.toString()),
            selection,
            selectionArgs.toTypedArray()
        )

        // Then
        assertThat(deleted).isZero()
    }

    @Test
    fun `𝕄 return 0 𝕎 update()`(
        @Forgery uri: URI,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>
    ) {
        // When
        val deleted = testedProvider.update(
            Uri.parse(uri.toString()),
            ContentValues(),
            selection,
            selectionArgs.toTypedArray()
        )

        // Then
        assertThat(deleted).isZero()
    }

    // endregion
}
