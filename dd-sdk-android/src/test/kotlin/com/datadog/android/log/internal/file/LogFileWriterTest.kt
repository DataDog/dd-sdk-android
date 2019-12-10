package com.datadog.android.log.internal.file

import com.datadog.android.log.internal.DataStorageCallback
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings()
internal class LogFileWriterTest {

    lateinit var underTest: LogFileWriter
    @Mock
    lateinit var mockFileOrchestrator: FileOrchestrator
    @Mock
    lateinit var mockCallback: DataStorageCallback

    @BeforeEach
    fun `set up`() {
        underTest = LogFileWriter(mockFileOrchestrator, mock(), mock())
    }

    @Test
    fun `when adding a callback it will delegate to fileOrchestrator`() {
        underTest.setCallback(mockCallback)

        verify(mockFileOrchestrator).setCallback(mockCallback)
    }

    @Test
    fun `when removing the callback it will delegate to fileOrchestrator`() {
        underTest.removeCallback()

        verify(mockFileOrchestrator).removeCallback()
    }
}
