/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Fetches the last line in the target [ByteArrayOutputStream].
 * @param charsetName the name of a supported [Charset]
 */
fun ByteArrayOutputStream.lastLine(charsetName: String = Charsets.UTF_8.name()): String? {
    return toString(charsetName)
        .split("\n")
        .lastOrNull { it.isNotBlank() }
}
