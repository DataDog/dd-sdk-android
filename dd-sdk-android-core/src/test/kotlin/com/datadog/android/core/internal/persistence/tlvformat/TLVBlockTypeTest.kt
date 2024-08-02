/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TLVBlockTypeTest {
    @Test
    fun `M return type value W fromValue() { existing value }`() {
        // When
        val shortValue = TLVBlockType.fromValue(TLVBlockType.VERSION_CODE.rawValue)

        // Then
        assertThat(shortValue).isEqualTo(TLVBlockType.VERSION_CODE)
    }

    @Test
    fun `M return null W fromValue() { nonexistent value }`() {
        // When
        val shortValue = TLVBlockType.fromValue(999u)

        // Then
        assertThat(shortValue).isNull()
    }
}
