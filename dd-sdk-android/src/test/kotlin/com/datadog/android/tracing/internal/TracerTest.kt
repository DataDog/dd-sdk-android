package com.datadog.android.tracing.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.tracing.Tracer
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
internal class TracerTest {

    lateinit var underTest: Tracer.TracerBuilder
    lateinit var mockAppContext: Application
    lateinit var fakeToken: String
    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeToken = forge.anHexadecimalString()
        mockAppContext = mockContext()
        Datadog.initialize(mockAppContext, fakeToken)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will build a valid Tracer`(forge: Forge) {
        // given
        underTest = Tracer.TracerBuilder()

        // when
        val tracer = underTest.setServiceName(fakeServiceName).build()
        val config = underTest.config()

        // then
        assertThat(tracer).isNotNull()
        assertThat(config).hasServiceName(fakeServiceName)
    }

    @Test
    fun `it will build a valid Tracer with a default service name if not provided`(forge: Forge) {
        // given
        underTest = Tracer.TracerBuilder()

        // when
        val tracer = underTest.build()
        val config = underTest.config()

        // then
        assertThat(tracer).isNotNull()
        assertThat(config).hasServiceName(Tracer.DEFAULT_SERVICE_NAME)
    }
}
