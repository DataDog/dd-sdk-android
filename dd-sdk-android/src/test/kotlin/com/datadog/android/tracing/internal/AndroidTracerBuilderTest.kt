package com.datadog.android.tracing.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.tracing.TracerBuilder
import com.datadog.android.tracing.assertj.TracerConfigAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class AndroidTracerBuilderTest {

    lateinit var underTest: TracerBuilder
    lateinit var mockAppContext: Application
    lateinit var fakeToken: String
    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()
        mockAppContext = mockContext()
        Datadog.initialize(mockAppContext, fakeToken)
        underTest = Datadog.tracerBuilder(fakeServiceName)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will build a valid DDTracer`(forge: Forge) {
        // when
        val tracer = underTest.build()
        val config = (underTest as AndroidTracerBuilder).config()

        // then
        assertThat(tracer).isNotNull()
        assertThat(config).hasServiceName(fakeServiceName)
    }
}
