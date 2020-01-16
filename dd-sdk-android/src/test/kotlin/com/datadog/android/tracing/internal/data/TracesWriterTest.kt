package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import datadog.opentracing.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
internal class TracesWriterTest {

    lateinit var underTest: TracesWriter

    @Mock
    lateinit var mockedFilesWriter: Writer<DDSpan>

    @BeforeEach
    fun `set up`() {
        underTest = TracesWriter(mockedFilesWriter)
    }

    @Test
    fun `when data received it will be handled by the wrapped file writer`(forge: Forge) {
        // given
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forge.getForgery())
            add(forge.getForgery())
        }

        // when
        underTest.write(spansList)

        // then
        val argumentCaptor = argumentCaptor<DDSpan>()
        verify(mockedFilesWriter, times(spansList.size)).write(argumentCaptor.capture())
        assertThat(argumentCaptor.allValues).containsExactlyElementsOf(spansList)
    }

    @Test
    fun `when null data received it will do nothing`(forge: Forge) {
        // when
        underTest.write(null)

        // then
        verifyZeroInteractions(mockedFilesWriter)
    }
}
