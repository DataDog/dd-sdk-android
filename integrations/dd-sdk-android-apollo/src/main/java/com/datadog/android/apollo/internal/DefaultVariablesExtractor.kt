/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.apollo.internal

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.variablesJson
import okio.IOException

/**
 * Default implementation of [VariablesExtractor] that uses Apollo's built-in variable extraction.
 */
internal class DefaultVariablesExtractor : VariablesExtractor {
    override fun extractVariables(operation: Operation<*>, adapters: CustomScalarAdapters): String? {
        return try {
            operation.variablesJson(adapters)
        } catch (_: IOException) {
            null
        }
    }
}
