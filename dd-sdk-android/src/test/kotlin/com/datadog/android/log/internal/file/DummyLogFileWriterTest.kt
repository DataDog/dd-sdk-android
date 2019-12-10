package com.datadog.android.log.internal.file

import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.DataStorageCallback
import com.datadog.android.log.internal.Log
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(ExtendWith(MockitoExtension::class),
        ExtendWith(ForgeExtension::class))
@ForgeConfiguration(Configurator::class)
internal class DummyLogFileWriterTest {

    lateinit var underTest: DummyLogWriter

    @BeforeEach
    fun `set up`() {
        underTest = DummyLogWriter()
    }

    @Test
    fun `a Dummy FileWriter should do nothing`(@Forgery fakeLog: Log) {
        underTest.writeLog(fakeLog) // there's nothing to test here
    }

    @Test
    fun `a Dummy FileWriter should do nothing when setting a callback`(@Forgery fakeLog: Log) {
        val callback = mock<DataStorageCallback>()
        underTest.setCallback(callback) // there's nothing to test here
        verifyZeroInteractions(callback)
    }

    @Test
    fun `a Dummy FileWriter should do nothing when removing a callback`(@Forgery fakeLog: Log) {
        underTest.removeCallback() // there's nothing to test here
    }
}
