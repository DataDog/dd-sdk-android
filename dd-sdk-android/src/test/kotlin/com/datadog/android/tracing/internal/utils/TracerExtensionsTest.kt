/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.utils

import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.getStaticValue
import datadog.opentracing.DDSpan
import datadog.opentracing.scopemanager.ContextualScopeManager
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

@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class, seed = 0x4f36670aL)
class TracerExtensionsTest {

    lateinit var tracer: AndroidTracer

    @BeforeEach
    fun `set up`() {
        tracer = AndroidTracer.Builder().build()
    }

    @AfterEach
    fun `tear down`() {
        val activeSpan = tracer.activeSpan()
        val activeScope = tracer.scopeManager().active()
        activeSpan?.finish()
        activeScope?.close()

        val tlsScope: ThreadLocal<*> = ContextualScopeManager::class.java.getStaticValue("tlsScope")
        tlsScope.remove()
    }

    @Test
    fun `it will return the trace id and span id if there is an active span`(forge: Forge) {

        // when
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        tracer.activateSpan(span)

        // then
        assertThat(tracer.traceId()).isEqualTo(span.traceId.toString())
        assertThat(tracer.spanId()).isEqualTo(span.spanId.toString())
    }

    @Test
    fun `it will return null for trace and span id if there is no active span`() {
        // given
        val tracer = AndroidTracer.Builder().build()

        // then
        assertThat(tracer.traceId()).isNull()
        assertThat(tracer.spanId()).isNull()
    }
}
