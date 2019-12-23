/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.constraints

import com.datadog.android.log.internal.utils.devLogger
import java.util.Locale

typealias StringTransform = (String) -> String?

internal class DatadogLogConstraints : LogConstraints {

    // region LogConstraints

    override fun validateTags(tags: List<String>): List<String> {
        val convertedTags = tags.mapNotNull {
            val tag = convertTag(it)
            if (tag == null) {
                devLogger.e("$TAG: \"$it\" is an invalid tag, and was ignored.")
            } else if (tag != it) {
                devLogger.w("$TAG: tag \"$it\" was modified to \"$tag\" to match our constraints.")
            }
            tag
        }
        val discardedCount = convertedTags.size - MAX_TAG_COUNT
        if (discardedCount > 0) {
            devLogger.w("$TAG: too many tags were added, $discardedCount had to be discarded.")
        }
        return convertedTags.take(MAX_TAG_COUNT)
    }

    override fun validateAttributes(attributes: Map<String, Any?>): Map<String, Any?> {
        val convertedAttributes = attributes.mapNotNull {
            val key = convertAttributeKey(it.key)
            if (key == null) {
                devLogger.e("$TAG: \"$it\" is an invalid attribute, and was ignored.")
                null
            } else {
                if (key != it.key) {
                    devLogger.w(
                        "$TAG: attribute \"${it.key}\" " +
                            "was modified to \"$key\" to match our constraints."
                    )
                }
                key to it.value
            }
        }
        val discardedCount = convertedAttributes.size - MAX_ATTR_COUNT
        if (discardedCount > 0) {
            devLogger.w(
                "$TAG: too many attributes were added, " +
                    "$discardedCount had to be discarded."
            )
        }
        return convertedAttributes.take(MAX_ATTR_COUNT).toMap()
    }

    // endregion

    // region Internal/Tag

    private val tagTransforms = listOf<StringTransform>(
        // Tags must be lowercase
        { it.toLowerCase(Locale.US) },
        // Tags must start with a letter
        { if (it.get(0) !in 'a'..'z') null else it },
        // Tags convert illegal characters to underscode
        { it.replace(Regex("[^a-z0-9_:./-]"), "_") },
        // Tags cannot end with a colon
        { if (it.endsWith(':')) it.substring(0, it.lastIndex) else it },
        // Tags can be up to 200 characters long
        { if (it.length > MAX_TAG_LENGTH) it.substring(0, MAX_TAG_LENGTH) else it },
        // Dismiss tags with reserved keys
        { if (isKeyReserved(it)) null else it }
    )

    private fun convertTag(rawTag: String?): String? {
        return tagTransforms.fold(rawTag) { tag, transform ->
            if (tag == null) null else transform.invoke(tag)
        }
    }

    private fun isKeyReserved(tag: String): Boolean {
        val firstColon = tag.indexOf(':')
        return if (firstColon > 0) {
            val key = tag.substring(0, firstColon)
            key in reservedTagKeys
        } else {
            false
        }
    }

    // endregion

    // region Internal/Attribute

    private val attributeKeyTransforms = listOf<StringTransform>(
        // A key can only have 10 levels
        { key ->
            var dotCount = 0
            val mapped = key.map {
                if (it == '.') {
                    dotCount++
                    if (dotCount > 9) '_' else it
                } else it
            }
            String(mapped.toCharArray())
        }
    )

    private fun convertAttributeKey(rawKey: String?): String? {
        return attributeKeyTransforms.fold(rawKey) { key, transform ->
            if (key == null) null else transform.invoke(key)
        }
    }

    // endregion

    companion object {
        private const val TAG = "DatadogLogConstraints"

        private const val MAX_TAG_LENGTH = 200
        private const val MAX_TAG_COUNT = 100

        private const val MAX_ATTR_KEY_LENGTH = 50
        private const val MAX_ATTR_COUNT = 256

        private val reservedTagKeys = setOf(
            "host", "device", "source", "service"
        )
    }
}
