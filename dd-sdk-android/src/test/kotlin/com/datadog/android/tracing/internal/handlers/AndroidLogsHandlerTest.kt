package com.datadog.android.tracing.internal.handlers

import com.datadog.android.log.Logger
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.times
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidLogsHandlerTest {

    lateinit var underTest: AndroidLogsHandler
    @Mock
    lateinit var mockedLogger: Logger

    @BeforeEach
    fun `set up`() {
        underTest = AndroidLogsHandler(
            mockedLogger
        )
    }

    @Test
    fun `when logging will delegate to the wrapped logger`(forge: Forge) {
        // given
        val logFields = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphabeticalString()
        }
        val logMessage = forge.anAlphabeticalString()
        val logTimestamp = forge.aLong()

        // when
        underTest.log(logMessage)
        underTest.log(logTimestamp, logMessage)
        underTest.log(logFields)
        underTest.log(logTimestamp, logFields)

        // then
        val inOrder = inOrder(mockedLogger)
        inOrder.verify(mockedLogger, times(2)).v(logMessage)
        logFields.forEach {
            inOrder.verify(mockedLogger).addTag(it.key, it.value)
        }
        logFields.forEach {
            inOrder.verify(mockedLogger).addTag(it.key, it.value)
        }
    }
}
