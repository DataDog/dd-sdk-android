/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

internal enum class TLVBlockType(val rawValue: UShort) {
    VERSION_CODE(0x00u),
    DATA(0x01u);

    companion object {
        private val map = values().associateBy { it.rawValue }

        fun fromValue(value: UShort): TLVBlockType? {
            return map[value]
        }
    }
}
