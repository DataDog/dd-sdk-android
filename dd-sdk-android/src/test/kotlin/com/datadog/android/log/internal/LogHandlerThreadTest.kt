package com.datadog.android.log.internal

import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.utils.accessMethod
import com.datadog.android.utils.extension.SystemOutputExtension
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class LogHandlerThreadTest {
    @Mock
    lateinit var mockLogReader: LogReader
    @Mock
    lateinit var mockLogWritter: LogWriter
    @Mock
    lateinit var mockLogUploader: LogUploader
    @Mock
    lateinit var mockLogUploadRunnable: UploadRunnable

    lateinit var underTest: LogHandlerThread

    @BeforeEach
    fun `set up`() {
        underTest = LogHandlerThread(mockLogReader, mockLogWritter,
            mockLogUploader, {
            mockLogUploadRunnable
        })
    }

    @Test
    fun `when looper prepared the runnable will be registered as a data storage callback`() {
        underTest.accessMethod("onLooperPrepared")

        verify(mockLogWritter).setCallback(mockLogUploadRunnable)
    }
}
