/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.constraints

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.constraints.StringTransform
import com.datadog.android.core.internal.utils.toMutableMap
import java.util.Locale

/**
 * Data constraints validator per Datadog requirements.
 *
 * @param internalLogger Internal logger.
 */
class DatadogDataConstraints(private val internalLogger: InternalLogger) : DataConstraints {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    // region DataConstraints

    /** @inheritdoc */
    override fun validateTags(tags: List<String>): List<String> {
        val convertedTags = tags.mapNotNull {
            val tag = convertTag(it)
            if (tag == null) {
                logger.logInvalidTagIgnored(tagValue = it)
            } else if (tag != it) {
                logger.logTagModifiedToMatchConstraints(originalTag = it, modifiedTag = tag)
            }
            tag
        }
        val discardedCount = convertedTags.size - MAX_TAG_COUNT
        if (discardedCount > 0) {
            logger.logTooManyTagsDiscarded(discardedCount = discardedCount)
        }
        return convertedTags.take(MAX_TAG_COUNT)
    }

    /** @inheritdoc */
    override fun <T : Any?> validateAttributes(
        attributes: Map<String, T>,
        keyPrefix: String?,
        attributesGroupName: String?,
        reservedKeys: Set<String>
    ): MutableMap<String, T> {
        // prefix = "a.b" => dotCount = 1+1 ("a.b." + key)
        val prefixDotCount = keyPrefix?.let { it.count { character -> character == '.' } + 1 } ?: 0
        val convertedAttributes = attributes.mapNotNull {
            // We need this in case the attributes are added from JAVA code and a null key may be
            // passed.
            @Suppress("SENSELESS_COMPARISON")
            if (it.key == null) {
                logger.logInvalidAttributeIgnored(attribute = it.toString())
                null
            } else if (it.key in reservedKeys) {
                logger.logAttributeKeyInReservedDropped(attribute = it.toString())
                null
            } else {
                val key = convertAttributeKey(it.key, prefixDotCount)
                if (key != it.key) {
                    logger.logAttributeKeyModifiedToMatchConstraints(
                        originalKey = it.key,
                        modifiedKey = key
                    )
                }
                key to it.value
            }
        }
        val discardedCount = convertedAttributes.size - MAX_ATTR_COUNT
        if (discardedCount > 0) {
            if (attributesGroupName != null) {
                logger.logTooManyAttributesDiscardedForGroup(
                    attributesGroupName = attributesGroupName,
                    discardedCount = discardedCount
                )
            } else {
                logger.logTooManyAttributesDiscarded(discardedCount = discardedCount)
            }
        }
        return convertedAttributes.take(MAX_ATTR_COUNT).toMutableMap()
    }

    /** @inheritdoc */
    override fun validateTimings(timings: Map<String, Long>): MutableMap<String, Long> {
        return timings.mapKeys { entry ->
            val sanitizedKey =
                entry.key.replace(Regex("[^a-zA-Z0-9\\-_.@$]"), "_")
            if (sanitizedKey != entry.key) {
                logger.logCustomTimingKeyReplaced(
                    originalKey = entry.key,
                    sanitizedKey = sanitizedKey
                )
            }
            sanitizedKey
        }.toMutableMap()
    }

    // endregion

    // region Internal/Tag

    @Suppress("UnsafeThirdPartyFunctionCall") // substring IndexOutOfBounds is impossible here
    private val tagTransforms = listOf<StringTransform>(
        // Tags must be lowercase
        { it.lowercase(Locale.US) },
        // Tags must start with a letter
        { if (it.getOrNull(0) !in 'a'..'z') null else it },
        // Tags convert illegal characters to underscore
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
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            if (tag == null) null else transform.invoke(tag)
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // substring IndexOutOfBounds is impossible here
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
            } else {
                it
            }
        }
        return String(mapped.toCharArray())
    }

    // endregion

    internal companion object {

        private const val MAX_TAG_LENGTH = 200
        private const val MAX_TAG_COUNT = 100

        private const val MAX_ATTR_COUNT = 128
        private const val MAX_DEPTH_LEVEL = 9

        private val reservedTagKeys = setOf(
            "host",
            "device",
            "source"
        )
    }
}
