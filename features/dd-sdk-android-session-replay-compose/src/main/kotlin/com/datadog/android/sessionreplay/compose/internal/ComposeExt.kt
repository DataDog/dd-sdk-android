/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal

import androidx.compose.runtime.tooling.CompositionGroup
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.ComposeFields
import com.datadog.android.sessionreplay.compose.internal.data.Parameter
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection

internal fun CompositionGroup.stableId(): Long {
    val identity = this.identity
    return if (ComposeReflection.AnchorClass.isInstance(identity)) {
        (ComposeReflection.AnchorLocationField.get(identity) as Int).toLong()
    } else {
        key.hashCode().toLong()
    }
}

internal fun CompositionGroup.parameters(context: ComposeContext): Sequence<ComposableParameter> {
    val block = data.firstOrNull {
        it != null && it.javaClass.name.endsWith(".RecomposeScopeImpl")
    }?.let {
        ComposeReflection.RecomposeScopeImplBlockField.get(it)
    } ?: return emptySequence()

    val composeFields = ComposeFields.from(block)

    val parametersMetadata = context.parameters ?: emptyList()

    return composeFields.paramFields.asSequence().mapIndexedNotNull { index, field ->
        val metadata = if (index < parametersMetadata.size) parametersMetadata[index] else Parameter(index)
        if (metadata.sortedIndex >= composeFields.paramFields.size) return@mapIndexedNotNull null
        val value = field.value.get(block)
        ComposableParameter(field.key, value)
    }
}

private const val defaultFieldName = "${'$'}${'$'}default"
private const val changedFieldName = "${'$'}changed"
private const val jacocoFieldName = "${'$'}jacoco"
