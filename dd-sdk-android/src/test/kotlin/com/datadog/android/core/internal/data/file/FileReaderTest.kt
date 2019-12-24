package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.log.forge.Configurator
import com.datadog.tools.unit.BuildConfig
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(SystemOutputExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
internal class FileReaderTest {

    lateinit var underTest: FileReader
    @TempDir
    lateinit var rootDir: File

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    @BeforeEach
    fun `set up`() {
        underTest = FileReader(mockOrchestrator, rootDir)
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
        val encodedData = Base64.getEncoder().encodeToString(data.toByteArray())
        file.writeText(encodedData)
        whenever(mockOrchestrator.getReadableFile(any())).thenReturn(file)

        // when
        val nextBatch = underTest.readNextBatch()

        // then
        assertThat(nextBatch?.logs).isNotNull.isNotEmpty
        assertThat((nextBatch?.logs ?: emptyList())[0]).isEqualTo(data)
    }

    @Test
    fun `returns a null batch if the file was already sent`() {
        // given
        whenever(mockOrchestrator.getReadableFile(any())).doReturn(null)

        // when
        val nextBatch = underTest.readNextBatch()

        // then
        assertThat(nextBatch).isNull()
    }

    @Test
    fun `if when orchestrator throws SecurityException will a null Batch`(
        @SystemOutStream systemOutStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        // given
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getReadableFile(any())

        // when
        val nextBatch = underTest.readNextBatch()

        // then
        if (BuildConfig.DEBUG) {
            val logMessages = systemOutStream.toString().trim().split("\n")
            assertThat(nextBatch).isNull()

            assertThat(logMessages[0]).matches("E/DD_LOG: FileReader: Couldn't access file .+")
        }
    }

    @Test
    fun `drops the batch if the file exists`(
        forge: Forge,
        @SystemOutStream systemOutStream: ByteArrayOutputStream
    ) {
        // given
        val fileName = forge.anAlphabeticalString()
        generateFile(fileName)

        // when
        underTest.dropBatch(fileName)

        // then
        val sentBatches = underTest.getFieldValue<MutableSet<String>>("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).contains(fileName)
        if (BuildConfig.DEBUG) {
            val logMessages = systemOutStream.toString().trim().split("\n")
            assertThat(logMessages[0])
                .matches("I/DD_LOG: FileReader: dropBatch $fileName")
        }
    }

    @Test
    fun `does nothing when trying to drop a batch for a file that doesn't exist`(
        forge: Forge,
        @SystemOutStream systemOutStream: ByteArrayOutputStream
    ) {
        // given
        val fileName = forge.anAlphabeticalString()
        val notExistingFile = File(rootDir, fileName)

        // when
        underTest.dropBatch(fileName)

        // then
        val sentBatches = underTest.getFieldValue<MutableSet<String>>("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).contains(fileName)
        if (BuildConfig.DEBUG) {
            val logMessages = systemOutStream.toString().trim().split("\n")
            assertThat(logMessages[1])
                .matches("W/DD_LOG: FileReader: file ${notExistingFile.path} does not exist.*")
        }
    }

    @Test
    fun `cleans the folder when dropping all batches`(
        forge: Forge,
        @SystemOutStream systemOutStream: ByteArrayOutputStream
    ) {
        // given
        val fileName1 = forge.anAlphabeticalString()
        val fileName2 = forge.anAlphabeticalString()
        val file1 = generateFile(fileName1)
        val file2 = generateFile(fileName2)
        whenever(mockOrchestrator.getAllFiles()).thenReturn(arrayOf(file1, file2))

        // when
        underTest.dropAllBatches()

        // then
        val sentBatches = underTest.getFieldValue<MutableSet<String>>("sentBatches")
        assertThat(rootDir.listFiles()).isEmpty()
        assertThat(sentBatches).isEmpty()
        if (BuildConfig.DEBUG) {
            val logMessages = systemOutStream.toString().trim().split("\n")
            assertThat(logMessages[0])
                .matches("I/DD_LOG: FileReader: dropAllBatches.*")
        }
    }

    private fun generateFile(fileName: String): File {
        val file = File(rootDir, fileName)
        file.createNewFile()
        return file
    }
}
