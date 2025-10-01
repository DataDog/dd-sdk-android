/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.apollo.internal

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation

/**
 * Interface for extracting variables from GraphQL operations.
 */
internal interface VariablesExtractor {
    /**
     * Extracts variables from a GraphQL operation as a JSON string.
     * @param operation The GraphQL operation
     * @param adapters Custom scalar adapters for serialization
     * @return JSON string representation of variables, or null if extraction fails
     */
    fun extractVariables(operation: Operation<*>, adapters: CustomScalarAdapters): String?
}
