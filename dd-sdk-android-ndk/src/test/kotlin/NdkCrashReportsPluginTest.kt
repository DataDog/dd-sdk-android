/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import android.content.Context
import com.datadog.android.ndk.NdkCrashReportsPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NdkCrashReportsPluginTest {

    lateinit var testedPlugin: NdkCrashReportsPlugin

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        testedPlugin = NdkCrashReportsPlugin()
    }

    @Test
    fun `M resolve to PENDING int state W consentToInt { PENDING }`() {
        assertThat(testedPlugin.consentToInt(TrackingConsent.PENDING)).isEqualTo(
            NdkCrashReportsPlugin.TRACKING_CONSENT_PENDING
        )
    }

    @Test
    fun `M resolve to GRANTED int state W consentToInt { GRANTED }`() {
        assertThat(testedPlugin.consentToInt(TrackingConsent.GRANTED)).isEqualTo(
            NdkCrashReportsPlugin.TRACKING_CONSENT_GRANTED
        )
    }

    @Test
    fun `M resolve to NOT_GRANTED int state W consentToInt { NOT_GRANTED }`() {
        assertThat(testedPlugin.consentToInt(TrackingConsent.NOT_GRANTED)).isEqualTo(
            NdkCrashReportsPlugin.TRACKING_CONSENT_NOT_GRANTED
        )
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M create the NDK crash reports directory W register { nativeLibrary loaded }`(
        trackingConsent: TrackingConsent,
        forge: Forge
    ) {
        // GIVEN
        val mockedContext: Context = mock {
            whenever(it.cacheDir).thenReturn(tempDir)
        }
        val config = DatadogPluginConfig(
            mockedContext,
            forge.anAlphabeticalString(),
            forge.anAlphabeticalString(),
            trackingConsent
        )
        testedPlugin.setFieldValue("nativeLibraryLoaded", true)

        // WHEN
        try {
            testedPlugin.register(config)
        } catch (e: UnsatisfiedLinkError) {
            // Do nothing. Just to avoid the NDK linkage error.
        }

        // THEN
        val ndkCrashDirectory = File(tempDir, NdkCrashReportsPlugin.NDK_CRASH_REPORTS_FOLDER)
        assertThat(ndkCrashDirectory.exists()).isTrue()
    }

    @ParameterizedTest
    @EnumSource(TrackingConsent::class)
    fun `M do nothing  W register { nativeLibrary not loaded }`(
        trackingConsent: TrackingConsent,
        forge: Forge
    ) {
        // GIVEN
        val mockedContext: Context = mock {
            whenever(it.cacheDir).thenReturn(tempDir)
        }
        val config = DatadogPluginConfig(
            mockedContext,
            forge.anAlphabeticalString(),
            forge.anAlphabeticalString(),
            trackingConsent
        )

        // WHEN
        try {
            testedPlugin.register(config)
        } catch (e: UnsatisfiedLinkError) {
            // Do nothing. Just to avoid the NDK linkage error.
        }

        // THEN
        val ndkCrashDirectory = File(tempDir, NdkCrashReportsPlugin.NDK_CRASH_REPORTS_FOLDER)
        assertThat(ndkCrashDirectory.exists()).isFalse()
    }
}
