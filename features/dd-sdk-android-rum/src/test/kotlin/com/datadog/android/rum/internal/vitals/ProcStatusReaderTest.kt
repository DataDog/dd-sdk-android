/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.vitals.ProcStatusReader.Field
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ProcStatusReaderTest {

    private lateinit var testedReader: ProcStatusReader

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @TempDir
    lateinit var fakeTempDir: File

    private lateinit var fakeStatusFile: File

    @BeforeEach
    fun `set up`() {
        fakeStatusFile = File(fakeTempDir, "status")
        testedReader = ProcStatusReader(internalLogger = mockInternalLogger, statusFile = fakeStatusFile)
    }

    @Test
    fun `M read proc self status W init()`() {
        // When
        val testedReader = ProcStatusReader(internalLogger = mockInternalLogger)

        // Then
        assertThat(testedReader.statusFile).isEqualTo(ProcStatusReader.STATUS_FILE)
    }

    @Test
    fun `M return requested fields in kibibytes W readFieldsKib()`(
        @LongForgery(min = 0L) fakeVmRssKib: Long,
        @LongForgery(min = 0L) fakeVmSwapKib: Long,
        @LongForgery(min = 0L) fakeRssFileKib: Long,
        @StringForgery(regex = "[a-z]+(\\.[a-z]+)+") fakeProcessName: String,
        @IntForgery(min = 1, max = 256) fakeThreads: Int
    ) {
        // Given
        fakeStatusFile.writeText(
            "Name:\t$fakeProcessName\n" +
                "VmRSS:\t${"$fakeVmRssKib kB".padStart(PADDED_VALUE_LENGTH)}\n" +
                "RssFile:\t$fakeRssFileKib kB\n" +
                "VmSwap:\t$fakeVmSwapKib kB\n" +
                "Threads:\t$fakeThreads\n"
        )

        // When
        val result = testedReader.readFieldsKib(listOf(Field.VM_RSS, Field.VM_SWAP))

        // Then
        assertThat(result).isEqualTo(mapOf(Field.VM_RSS to fakeVmRssKib, Field.VM_SWAP to fakeVmSwapKib))
    }

    @Test
    fun `M skip missing and unparsable fields W readFieldsKib()`(
        @LongForgery(min = 0L) fakeVmRssKib: Long,
        @StringForgery(regex = "[a-z]+") fakeNotANumber: String
    ) {
        // Given
        fakeStatusFile.writeText("VmRSS:\t$fakeVmRssKib kB\nVmSwap:\t$fakeNotANumber kB\n")

        // When
        val result = testedReader.readFieldsKib(listOf(Field.VM_RSS, Field.VM_SWAP, Field.RSS_ANON))

        // Then
        assertThat(result).isEqualTo(mapOf(Field.VM_RSS to fakeVmRssKib))
    }

    @Test
    fun `M return empty map W readFieldsKib() { file has invalid data }`(
        @StringForgery fakeContent: String
    ) {
        // Given
        fakeStatusFile.writeText(fakeContent)

        // When
        val result = testedReader.readFieldsKib(listOf(Field.VM_RSS))

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M not match a field by prefix W readFieldsKib()`(
        @LongForgery(min = 0L) fakeVmRssKib: Long
    ) {
        // Given
        fakeStatusFile.writeText("VmRSSExtra:\t$fakeVmRssKib kB\n")

        // When
        val result = testedReader.readFieldsKib(listOf(Field.VM_RSS))

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W readFieldsKib() { file doesn't exist }`() {
        // When
        val result = testedReader.readFieldsKib(listOf(Field.VM_RSS))

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W readFieldsKib() { file isn't readable }`() {
        // Given
        val stubFile = mock<File> {
            on { exists() } doReturn true
            on { canRead() } doReturn false
        }

        // When
        val result = ProcStatusReader(mockInternalLogger, stubFile).readFieldsKib(listOf(Field.VM_RSS))

        // Then
        assertThat(result).isEmpty()
    }

    private companion object {
        const val PADDED_VALUE_LENGTH = 11
    }
}
