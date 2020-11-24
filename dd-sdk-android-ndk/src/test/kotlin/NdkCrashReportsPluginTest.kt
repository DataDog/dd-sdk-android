/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.android.ndk.NdkCrashReportsPlugin
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
}
