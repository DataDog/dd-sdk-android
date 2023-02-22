/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.storage

import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class WebViewEventSerializerTest {

    lateinit var testedSerializer: WebViewEventSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = WebViewEventSerializer()
    }

    @Test
    fun `serializes full log as json`(@Forgery fakeLog: JsonObject) {
        val serialized = testedSerializer.serialize(fakeLog)
        assertThat(serialized).isEqualTo(fakeLog.toString())
    }
}
