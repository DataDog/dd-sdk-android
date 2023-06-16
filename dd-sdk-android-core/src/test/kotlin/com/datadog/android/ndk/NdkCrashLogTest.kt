/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(Configurator::class)
internal class NdkCrashLogTest {

    @Test
    fun `M deserialize an NdkCrashLog W required`(@Forgery fakeNdkCrashLog: NdkCrashLog) {
        // GIVEN
        val serializedLog = fakeNdkCrashLog.toJson()

        // WHEN
        val deserializedLog: NdkCrashLog = NdkCrashLog.fromJson(serializedLog)

        // THEN
        assertThat(deserializedLog).isEqualTo(fakeNdkCrashLog)
    }
}
