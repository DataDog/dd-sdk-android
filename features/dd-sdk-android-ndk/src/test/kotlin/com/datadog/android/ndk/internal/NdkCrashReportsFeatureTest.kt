/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class NdkCrashReportsFeatureTest {

    private lateinit var testedFeature: NdkCrashReportsFeature

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        testedFeature = NdkCrashReportsFeature(mockSdkCore)
    }

    @Test
    fun `M resolve to PENDING int state W consentToInt { PENDING }`() {
        assertThat(testedFeature.consentToInt(TrackingConsent.PENDING)).isEqualTo(
            NdkCrashReportsFeature.TRACKING_CONSENT_PENDING
        )
    }

    @Test
    fun `M resolve to GRANTED int state W consentToInt { GRANTED }`() {
        assertThat(testedFeature.consentToInt(TrackingConsent.GRANTED)).isEqualTo(
            NdkCrashReportsFeature.TRACKING_CONSENT_GRANTED
        )
    }

    @Test
    fun `M resolve to NOT_GRANTED int state W consentToInt { NOT_GRANTED }`() {
        assertThat(testedFeature.consentToInt(TrackingConsent.NOT_GRANTED)).isEqualTo(
            NdkCrashReportsFeature.TRACKING_CONSENT_NOT_GRANTED
        )
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M create the NDK crash reports directory W onInitialize { nativeLibrary loaded }`(
        trackingConsent: TrackingConsent
    ) {
        // GIVEN
        val mockContext: Context = mock()
        whenever(mockSdkCore.rootStorageDir) doReturn tempDir
        whenever(mockSdkCore.trackingConsent) doReturn trackingConsent
        whenever(mockSdkCore.internalLogger) doReturn mock()
        testedFeature.setFieldValue("nativeLibraryLoaded", true)

        // WHEN
        try {
            testedFeature.onInitialize(mockContext)
        } catch (e: UnsatisfiedLinkError) {
            // Do nothing. Just to avoid the NDK linkage error.
        }

        // THEN
        val ndkCrashDirectory = File(tempDir, NdkCrashReportsFeature.NDK_CRASH_REPORTS_FOLDER)
        assertThat(ndkCrashDirectory.exists()).isTrue()
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M do nothing  W register { nativeLibrary not loaded }`(
        trackingConsent: TrackingConsent
    ) {
        // GIVEN
        val mockContext: Context = mock()
        whenever(mockSdkCore.rootStorageDir) doReturn tempDir
        whenever(mockSdkCore.trackingConsent) doReturn trackingConsent
        whenever(mockSdkCore.internalLogger) doReturn mock()

        // WHEN
        try {
            testedFeature.onInitialize(mockContext)
        } catch (e: UnsatisfiedLinkError) {
            // Do nothing. Just to avoid the NDK linkage error.
        }

        // THEN
        val ndkCrashDirectory = File(tempDir, NdkCrashReportsFeature.NDK_CRASH_REPORTS_FOLDER)
        assertThat(ndkCrashDirectory.exists()).isFalse()
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M do nothing  W register { no root storage dir }`(
        trackingConsent: TrackingConsent
    ) {
        // GIVEN
        val mockContext: Context = mock()
        whenever(mockSdkCore.rootStorageDir) doReturn null
        whenever(mockSdkCore.trackingConsent) doReturn trackingConsent
        val mockInternalLogger = mock<InternalLogger>()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        testedFeature.setFieldValue("nativeLibraryLoaded", true)

        // WHEN
        try {
            testedFeature.onInitialize(mockContext)
        } catch (e: UnsatisfiedLinkError) {
            // Do nothing. Just to avoid the NDK linkage error.
        }

        // THEN
        val ndkCrashDirectory = File(tempDir, NdkCrashReportsFeature.NDK_CRASH_REPORTS_FOLDER)
        assertThat(ndkCrashDirectory.exists()).isFalse()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger)
                .log(
                    eq(InternalLogger.Level.WARN),
                    eq(InternalLogger.Target.USER),
                    capture(),
                    isNull(),
                    eq(false)
                )
            assertThat(lastValue()).isEqualTo(NdkCrashReportsFeature.NO_SDK_ROOT_DIR_MESSAGE)
        }
    }
}
