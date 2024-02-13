/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.collection.LruCache
import androidx.compose.runtime.tooling.CompositionGroup

internal data class ComposeContext(
    val name: String? = null,
    val sourceFile: String? = null,
    val packageHash: Int = -1,
    val locations: List<SourceLocationInfo> = emptyList(),
    val repeatOffset: Int = -1,
    val parameters: List<Parameter>? = null,
    val isCall: Boolean = false,
    val isInline: Boolean = false
) {

    companion object {

        // TODO : find which would be better here (LFU, LRU, …)
        internal val contextCache = object : LruCache<String, ComposeContext>(256) {
            override fun create(key: String): ComposeContext? {
                return ComposeContextLexer.parse(key)
            }
        }

        internal fun from(compositionGroup: CompositionGroup): ComposeContext? {
            val sourceInfo = compositionGroup.sourceInfo ?: return null
            return contextCache.get(sourceInfo)
        }
    }
}
