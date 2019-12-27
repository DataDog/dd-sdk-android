package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.log.forge.Configurator
import com.datadog.tools.unit.BuildConfig
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
internal class FileWriterTest {
    lateinit var underTest: FileWriter<String>
    @Mock
    lateinit var mockedSerializer: Serializer<String>
    @Mock
    lateinit var mockedOrchestrator: Orchestrator
    @Mock
    lateinit var mockDeferredHandler: AndroidDeferredHandler

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockedSerializer.serialize(any())).doAnswer {
            it.getArgument(0)
        }
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        underTest = FileWriter(mockedOrchestrator, rootDir, mockedSerializer)
        underTest.deferredHandler = mockDeferredHandler
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes a valid mode`(forge: Forge) {
        val modelValue = forge.anAlphabeticalString()
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = generateFile(fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        // when
        underTest.write(modelValue)

        // then
        // TODO: RUMM-135 assert also the content of the file. We will not do it now because
        //  we will remove the obfuscation anyway.
        assertThat(file.readText()).isNotEmpty()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `does nothing when SecurityException was thrown while providing a file`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val modelValue = forge.anAlphabeticalString()
        val securityException = SecurityException(forge.anAlphabeticalString())

        doThrow(securityException).whenever(mockedOrchestrator).getWritableFile(any())

        // when
        underTest.write(modelValue)

        // then
        if (BuildConfig.DEBUG) {
            val logMessages = outputStream.toString().trim().split("\n")
            assertThat(logMessages[0]).matches("E/DD_LOG: FileWriter: Couldn't access file.*")
        }
    }

    @AfterEach
    fun `tear down`() {
        rootDir.deleteRecursively()
    }

    private fun generateFile(fileName: String): File {
        val file = File(rootDir, fileName)
        file.createNewFile()
        return file
    }
}
