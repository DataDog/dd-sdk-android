package com.datadog.android.log.internal.file

import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.domain.Log
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
}
