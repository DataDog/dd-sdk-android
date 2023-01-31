/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.utils

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.TimeZone

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class LogUtilsTest {

    @Test
    fun `M build a SimpleDateFormat with the ISO format W buildSimpleDateFormat()`() {
        // When
        val simpleDateFormat = buildLogDateFormat()

        // Then
        assertThat(simpleDateFormat.toPattern()).isEqualTo(ISO_8601)
        assertThat(simpleDateFormat.timeZone).isEqualTo(TimeZone.getTimeZone("UTC"))
    }
}
