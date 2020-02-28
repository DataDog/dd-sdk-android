package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.resolveTagName
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class, seed = 0x146ba9a6L)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ImmediateFileWriterTest {

    lateinit var underTest: ImmediateFileWriter<String>
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
        underTest = ImmediateFileWriter(
            mockedOrchestrator,
            mockedSerializer
        )
    }

    @AfterEach
    fun `tear down`() {
        rootDir.deleteRecursively()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes a valid model`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        underTest.write(model)

        assertThat(file.readText())
            .isEqualTo(model)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes several models`(forge: Forge) {
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        models.forEach {
            underTest.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(models.joinToString(","))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `does nothing when SecurityException was thrown while providing a file`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val modelValue = forge.anAlphabeticalString()
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockedOrchestrator).getWritableFile(any())
        val expectedLogcatTag = resolveTagName(underTest, "DD_LOG")

        underTest.write(modelValue)

        if (BuildConfig.DEBUG) {
            val logMessages = outputStream.toString().trim().split("\n")
            assertThat(logMessages[0]).matches("E/$expectedLogcatTag: Couldn't access file .*")
        }
    }

    @Test
    fun `does nothing when FileOrchestrator returns a null file`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        val modelValue = forge.anAlphabeticalString()
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(null)
        val expectedLogcatTag = resolveTagName(underTest, "DD_LOG")

        // when
        underTest.write(modelValue)

        // then
        if (BuildConfig.DEBUG) {
            val logMessages = outputStream.toString().trim().split("\n")
            assertThat(logMessages[0])
                .matches("E/$expectedLogcatTag: Could not get a valid file")
        }
    }
}
