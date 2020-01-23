package com.datadog.android.core.internal.data.file

import android.os.Build
import android.util.Log
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.resolveTagName
import com.datadog.tools.unit.BuildConfig
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemStreamExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemStreamExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
internal class FileReaderTest {

    lateinit var testedReader: FileReader

    @TempDir
    lateinit var rootDir: File

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    lateinit var prefix: String
    lateinit var suffix: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        prefix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
        suffix = forge.anAsciiString(size = forge.anInt(min = 2, max = 8))
        testedReader = FileReader(mockOrchestrator, rootDir, prefix, suffix)
    }

    @Test
    fun `doesn't ask for the same batch twice in a row`(
        forge: Forge
    ) {
        val fileName = forge.anAlphabeticalString()
        val file = generateFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(any())) doReturn null
        whenever(mockOrchestrator.getReadableFile(emptySet())) doReturn file

        val firstBatch = testedReader.readNextBatch()
        val secondBatch = testedReader.readNextBatch()
        checkNotNull(firstBatch)

        assertThat(String(firstBatch.data)).isEqualTo("$prefix$data$suffix")
        assertThat(secondBatch).isNull()
        inOrder(mockOrchestrator) {
            verify(mockOrchestrator).getReadableFile(emptySet())
            verify(mockOrchestrator).getReadableFile(setOf(firstBatch.id))
            verifyNoMoreInteractions(mockOrchestrator)
        }
    }

    @Test
    fun `reads a batch that was previously read then released`(
        forge: Forge
    ) {
        val fileName = forge.anAlphabeticalString()
        val file = generateFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(any())) doReturn null
        whenever(mockOrchestrator.getReadableFile(emptySet())) doReturn file

        val firstBatch = testedReader.readNextBatch()
        val secondBatch = testedReader.readNextBatch()
        checkNotNull(firstBatch)
        testedReader.releaseBatch(firstBatch.id)
        val thirdBatch = testedReader.readNextBatch()
        checkNotNull(thirdBatch)

        assertThat(String(firstBatch.data)).isEqualTo("$prefix$data$suffix")
        assertThat(secondBatch).isNull()
        assertThat(String(thirdBatch.data)).isEqualTo("$prefix$data$suffix")
        inOrder(mockOrchestrator) {
            verify(mockOrchestrator).getReadableFile(emptySet())
            verify(mockOrchestrator).getReadableFile(setOf(firstBatch.id))
            verify(mockOrchestrator).getReadableFile(emptySet())
            verifyNoMoreInteractions(mockOrchestrator)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `returns a valid batch if file exists and valid`(
        forge: Forge
    ) {
        // given
        val fileName = forge.anAlphabeticalString()
        val file = generateFile(fileName)
        val data = forge.anAlphabeticalString()
        file.writeText(data)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)

        // when
        val nextBatch = testedReader.readNextBatch()
        checkNotNull(nextBatch)

        // then
        val persistedData = String(nextBatch.data)
        assertThat(persistedData).isEqualTo("$prefix$data$suffix")
    }

    @Test
    fun `returns a null batch if the file was already sent`() {
        // given
        whenever(mockOrchestrator.getReadableFile(any())).doReturn(null)

        // when
        val nextBatch = testedReader.readNextBatch()

        // then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `returns a null batch if the data is corrupted`() {
        // given
        whenever(mockOrchestrator.getReadableFile(any())).doReturn(null)

        // when
        val nextBatch = testedReader.readNextBatch()

        // then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `returns null when orchestrator throws SecurityException`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        // given
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getReadableFile(any())

        // when
        val nextBatch = testedReader.readNextBatch()

        // then
        assertThat(nextBatch).isNull()
        if (BuildConfig.DEBUG) {
            val expectedTag = resolveTagName(testedReader, "DD_LOG")
            assertThat(outputStream)
                .hasLogLine(Log.ERROR, expectedTag, startsWith("Couldn't access file"))
        }
    }

    @Test
    fun `returns null when orchestrator throws OutOfMemoryError`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        // given
        val exception = OutOfMemoryError(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getReadableFile(any())

        // when
        val nextBatch = testedReader.readNextBatch()

        // then
        assertThat(nextBatch).isNull()
        if (BuildConfig.DEBUG) {
            val expectedTag = resolveTagName(testedReader, "DD_LOG")
            assertThat(outputStream)
                .hasLogLine(Log.ERROR, expectedTag, startsWith("Couldn't read file"))
        }
    }

    @Test
    fun `drops the batch if the file exists`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        // given
        val fileName = forge.anAlphabeticalString()
        generateFile(fileName)

        // when
        testedReader.dropBatch(fileName)

        // then
        val sentBatches: MutableSet<String> = testedReader.getFieldValue("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).contains(fileName)
        if (BuildConfig.DEBUG) {
            val expectedTag = resolveTagName(testedReader, "DD_LOG")
            assertThat(outputStream)
                .hasLogLine(Log.INFO, expectedTag, "dropBatch $fileName")
        }
    }

    @Test
    fun `does nothing when trying to drop a batch for a file that doesn't exist`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        // given
        val fileName = forge.anAlphabeticalString()
        val notExistingFile = File(rootDir, fileName)

        // when
        testedReader.dropBatch(fileName)

        // then
        val sentBatches: MutableSet<String> = testedReader.getFieldValue("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).contains(fileName)
        if (BuildConfig.DEBUG) {
            val expectedTag = resolveTagName(testedReader, "DD_LOG")
            assertThat(outputStream)
                .hasLogLine(
                    Log.WARN, expectedTag,
                    startsWith("file ${notExistingFile.path} does not exist")
                )
        }
    }

    @Test
    fun `cleans the folder when dropping all batches`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        // given
        val fileName1 = forge.anAlphabeticalString()
        val fileName2 = forge.anAlphabeticalString()
        val file1 = generateFile(fileName1)
        val file2 = generateFile(fileName2)
        whenever(mockOrchestrator.getAllFiles()).thenReturn(arrayOf(file1, file2))

        // when
        testedReader.dropAllBatches()

        // then
        val sentBatches: MutableSet<String> = testedReader.getFieldValue("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).isEmpty()
        if (BuildConfig.DEBUG) {
            val expectedTag = resolveTagName(testedReader, "DD_LOG")
            assertThat(outputStream)
                .hasLogLine(Log.INFO, expectedTag, startsWith("dropAllBatches"))
        }
    }

    private fun generateFile(fileName: String): File {
        val file = File(rootDir, fileName)
        file.createNewFile()
        return file
    }
}
