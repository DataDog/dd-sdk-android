/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.compose.ui.text.font.FontFamily
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
class ComposeFieldsTest {

    @Test
    fun `M read all expected fields W from()`(forge: Forge) {
        val instance = FakeComposeDataClass(forge)

        val composeFields = ComposeFields.from(instance)

        val paramFields = composeFields.paramFields
        assertThat(paramFields["string"]?.get(instance)).isEqualTo(instance.getString())
        assertThat(paramFields["long"]?.get(instance)).isEqualTo(instance.getLong())
        assertThat(paramFields["fontFamily"]?.get(instance)).isEqualTo(instance.getFontFamily())

        assertThat(paramFields["ignoredField"]).isNull()
        assertThat(paramFields["\$ignoredField"]).isNull()
        assertThat(paramFields["\$\$ignoredField"]).isNull()
    }
}

class FakeComposeDataClass(forge: Forge) {

    // Ignored because it doesn't have the $ prefix
    @Suppress("UnusedPrivateProperty")
    private val ignoredField: String = forge.aString()

    // Ignored because it has two $ prefix
    @Suppress("UnusedPrivateProperty")
    private val `$$ignoredField`: String = forge.aString()

    private val `$string`: String = forge.aString()
    private val `$long`: Long = forge.aLong()
    private val `$fontFamily`: FontFamily = forge.anElementFrom(
        FontFamily.Cursive,
        FontFamily.Monospace,
        FontFamily.SansSerif,
        FontFamily.Serif,
        FontFamily.Default
    )

    fun getString(): String = `$string`
    fun getLong(): Long = `$long`
    fun getFontFamily(): FontFamily = `$fontFamily`
}
