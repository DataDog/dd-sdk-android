/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FlagsClientEventDetailsTest {
    @Suppress("DontDowncastCollectionTypes") // Deliberately probe Java-style mutation of the exposed collections.
    @Test
    fun `M defensively copy immutable collections W construction`() {
        val keys = mutableListOf("key")
        val metadata = mutableMapOf<String, FlagsEventMetadataValue>(
            "string" to FlagsEventMetadataValue.StringValue("value"),
            "boolean" to FlagsEventMetadataValue.BooleanValue(true),
            "long" to FlagsEventMetadataValue.LongValue(Long.MAX_VALUE),
            "double" to FlagsEventMetadataValue.DoubleValue(0.5)
        )
        val details = FlagsClientEventDetails("provider", keys, "message", FlagsEventErrorCode.GENERAL, metadata)
        keys.clear()
        metadata.clear()
        assertThat(details.flagsChanged).containsExactly("key")
        assertThat(details.eventMetadata).hasSize(4)
        assertThat(details.eventMetadata["long"]).isEqualTo(FlagsEventMetadataValue.LongValue(Long.MAX_VALUE))
        assertThrows<UnsupportedOperationException> { (details.flagsChanged as MutableList).clear() }
        assertThrows<UnsupportedOperationException> { (details.eventMetadata as MutableMap).clear() }
    }
}
