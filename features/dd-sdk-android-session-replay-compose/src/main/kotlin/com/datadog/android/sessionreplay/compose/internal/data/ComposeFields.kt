/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import com.datadog.android.sessionreplay.compose.internal.reflection.accessible
import java.lang.reflect.Field

internal data class ComposeFields(
    val clazz: Class<*>,
    val paramFields: Map<String, Field>
) {

    companion object {
        private const val PARAMETER_FIELD_PREFIX = "${'$'}"
        private const val INTERNAL_FIELD_PREFIX = "${'$'}${'$'}"

        // Because composable classes are reused, we only need to parse them once
        internal val fieldsCache = mutableMapOf<String, ComposeFields>()

        internal fun from(block: Any): ComposeFields {
            val clazz = block.javaClass
            val clazzName = clazz.canonicalName ?: clazz.name

            if (fieldsCache.containsKey(clazzName)) {
                fieldsCache[clazzName]?.let {
                    return it
                }
            }

            val paramFields = mutableMapOf<String, Field>()

            clazz.declaredFields.forEach {
                if (it.name.startsWith(PARAMETER_FIELD_PREFIX) && !it.name.startsWith(INTERNAL_FIELD_PREFIX)) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // substring can't throw OutOfBounds
                    paramFields[it.name.substring(1)] = it.accessible()
                }
            }
            val composeFields = ComposeFields(clazz, paramFields)

            fieldsCache[clazzName] = composeFields

            return composeFields
        }
    }
}
