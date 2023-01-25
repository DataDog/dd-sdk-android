/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EnrichedRecordTest {

    @Test
    fun `M serialize a record to a JSON string W toJson()`(
        @Forgery fakeEnrichedRecord: EnrichedRecord
    ) {
        // When
        val serializedObject = fakeEnrichedRecord.toJson()

        // Then
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject.get(EnrichedRecord.APPLICATION_ID_KEY).asString)
            .isEqualTo(fakeEnrichedRecord.applicationId)
        assertThat(jsonObject.get(EnrichedRecord.SESSION_ID_KEY).asString)
            .isEqualTo(fakeEnrichedRecord.sessionId)
        assertThat(jsonObject.get(EnrichedRecord.VIEW_ID_KEY).asString)
            .isEqualTo(fakeEnrichedRecord.viewId)
        val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
            .asJsonArray
            .toList()
            .map { it.toString() }
        val expectedRecords = fakeEnrichedRecord.records.map { it.toJson().toString() }
        assertThat(records).isEqualTo(expectedRecords)
    }
}
