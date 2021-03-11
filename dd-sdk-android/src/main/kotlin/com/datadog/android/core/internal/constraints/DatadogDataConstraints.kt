/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.constraints

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.model.ViewEvent
import java.util.Locale

internal typealias StringTransform = (String) -> String?

internal class DatadogDataConstraints : DataConstraints {

    // region DataConstraints

    override fun validateTags(tags: List<String>): List<String> {
        val convertedTags = tags.mapNotNull {
            val tag = convertTag(it)
            if (tag == null) {
                devLogger.e("\"$it\" is an invalid tag, and was ignored.")
            } else if (tag != it) {
                devLogger.w("tag \"$it\" was modified to \"$tag\" to match our constraints.")
            }
            tag
        }
        val discardedCount = convertedTags.size - MAX_TAG_COUNT
        if (discardedCount > 0) {
            devLogger.w("too many tags were added, $discardedCount had to be discarded.")
        }
        return convertedTags.take(MAX_TAG_COUNT)
    }

    override fun validateAttributes(
        attributes: Map<String, Any?>,
        keyPrefix: String?,
        attributesGroupName: String?
    ): Map<String, Any?> {

        // prefix = "a.b" => dotCount = 1+1 ("a.b." + key)
        val prefixDotCount = keyPrefix?.let { it.count { character -> character == '.' } + 1 } ?: 0
        val convertedAttributes = attributes.mapNotNull {
            // We need this in case the attributes are added from JAVA code and a null key may be
            // passed.
            if (it.key == null) {
                devLogger.e("\"$it\" is an invalid attribute, and was ignored.")
                null
            }
            val key = convertAttributeKey(it.key, prefixDotCount)
            if (key != it.key) {
                devLogger.w(
                    "Key \"${it.key}\" " +
                        "was modified to \"$key\" to match our constraints."
                )
            }
            key to it.value
        }
        val discardedCount = convertedAttributes.size - MAX_ATTR_COUNT
        if (discardedCount > 0) {
            val warningMessage = resolveDiscardedAttrsWarning(
                attributesGroupName,
                discardedCount
            )
            devLogger.w(warningMessage)
        }
        return convertedAttributes.take(MAX_ATTR_COUNT).toMap()
    }

    override fun validateEvent(rumEvent: Any): Any {
        return if (rumEvent is ViewEvent) {

            val customTimings = rumEvent.view.customTimings?.let {
                it.copy(
                    additionalProperties = it.additionalProperties.mapKeys { entry ->
                        val sanitizedKey =
                            entry.key.replace(Regex("[^a-zA-Z0-9\\-_.@$]"), "_")
                        if (sanitizedKey != entry.key) {
                            devLogger.w(
                                CUSTOM_TIMING_KEY_REPLACED_WARNING.format(
                                    Locale.US,
                                    entry.key,
                                    sanitizedKey
                                )
                            )
                        }
                        sanitizedKey
                    }
                )
            }

            rumEvent.copy(
                view = rumEvent.view.copy(
                    customTimings = customTimings
                )
            )
        } else {
            rumEvent
        }
    }

    private fun resolveDiscardedAttrsWarning(
        attributesGroupName: String?,
        discardedCount: Int
    ): String {
        return if (attributesGroupName != null) {
            "Too many attributes were added for [$attributesGroupName], " +
                "$discardedCount had to be discarded."
        } else {
            "Too many attributes were added, " +
                "$discardedCount had to be discarded."
        }
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

    private fun convertAttributeKey(rawKey: String, prefixDotCount: Int): String {
        var dotCount = prefixDotCount
        val mapped = rawKey.map {
            if (it == '.') {
                dotCount++
                if (dotCount > MAX_DEPTH_LEVEL) '_' else it
            } else it
        }
        return String(mapped.toCharArray())
    }

    // endregion

    companion object {

        private const val MAX_TAG_LENGTH = 200
        private const val MAX_TAG_COUNT = 100

        private const val MAX_ATTR_COUNT = 128
        private const val MAX_DEPTH_LEVEL = 9

        internal const val CUSTOM_TIMING_KEY_REPLACED_WARNING = "Invalid timing name: %s," +
            " sanitized to: %s"

        private val reservedTagKeys = setOf(
            "host",
            "device",
            "source",
            "service"
        )
    }
}
