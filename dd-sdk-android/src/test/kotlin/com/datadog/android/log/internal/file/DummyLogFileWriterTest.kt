package com.datadog.android.log.internal.file

import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.Log
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

    lateinit var underTest: DummyFileWriter

    @BeforeEach
    fun `set up`() {
        underTest = DummyFileWriter()
    }

    @Test
    fun `a Dummy FileWriter should do nothing`(@Forgery fakeLog: Log) {
        underTest.writeLog(fakeLog) // there's nothing to test here
    }
}
