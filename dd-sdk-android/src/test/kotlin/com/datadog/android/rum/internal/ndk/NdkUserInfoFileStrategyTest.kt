/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class NdkUserInfoFileStrategyTest {

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @TempDir
    lateinit var tempRootDir: File

    lateinit var fakeNdkDir: File

    lateinit var fakeNdkTempDir: File

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    lateinit var testedFileStrategy: NdkUserInfoFileStrategy

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeNdkTempDir = File(tempRootDir, forge.anAlphabeticalString())
        fakeNdkDir = File(tempRootDir, forge.anAlphabeticalString())
        whenever(mockConsentProvider.getConsent()).thenReturn(
            forge.aValueFrom(TrackingConsent::class.java)
        )
        testedFileStrategy = NdkUserInfoFileStrategy(
            fakeNdkTempDir,
            fakeNdkDir,
            mockExecutorService,
            mockConsentProvider
        )
    }

    @Test
    fun `M use the appropriate NdkFileOrchestrator W writing in temp folder`(forge: Forge) {
        // WHEN
        val writableFile =
            testedFileStrategy.intermediateFileOrchestrator.getWritableFile(forge.anInt())

        // THEN
        assertThat(writableFile?.absolutePath).isEqualTo(
            File(
                fakeNdkTempDir,
                DatadogNdkCrashHandler.LAST_USER_INFORMATION_FILE_NAME
            ).absolutePath
        )
    }

    @Test
    fun `M use the appropriate NdkFileOrchestrator W writing in authorized folder`(forge: Forge) {
        // WHEN
        val writableFile =
            testedFileStrategy.authorizedFileOrchestrator.getWritableFile(forge.anInt())

        // THEN
        assertThat(writableFile?.absolutePath).isEqualTo(
            File(
                fakeNdkDir,
                DatadogNdkCrashHandler.LAST_USER_INFORMATION_FILE_NAME
            ).absolutePath
        )
    }
}
