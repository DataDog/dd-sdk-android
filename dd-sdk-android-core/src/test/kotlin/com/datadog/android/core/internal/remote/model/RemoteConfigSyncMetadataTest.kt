/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote.model

import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings
@ForgeConfiguration(Configurator::class)
internal class RemoteConfigSyncMetadataTest {

    @RepeatedTest(8)
    fun `M serialize deserialized event W toJson()+fromJson()`(
        @Forgery fakeMetadata: RemoteConfigSyncMetadata
    ) {
        // Given
        val json = fakeMetadata.toJson().toString()

        // When
        val result = RemoteConfigSyncMetadata.fromJson(json)

        // Then
        assertThat(result).isEqualTo(fakeMetadata)
    }

    @Test
    fun `M throw JsonParseException W fromJson() { malformed json string }`() {
        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJson("not-valid-json{{{")
        }

        // Then
        assertThat(exception.message).contains("Unable to parse json into type RemoteConfigSyncMetadata")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { configId missing }`(
        @LongForgery fakeLastSynced: Long,
        @StringForgery fakeSyncId: String
    ) {
        // Given — configId key is entirely absent
        val json = JsonObject().apply {
            addProperty("lastSynced", fakeLastSynced)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing configId")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { configId explicit null }`(
        @LongForgery fakeLastSynced: Long,
        @StringForgery fakeSyncId: String
    ) {
        // Given — configId key is present but holds a JSON null, not merely absent
        val json = JsonObject().apply {
            add("configId", JsonNull.INSTANCE)
            addProperty("lastSynced", fakeLastSynced)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing configId")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { lastSynced missing }`(
        @StringForgery fakeConfigId: String,
        @StringForgery fakeSyncId: String
    ) {
        // Given
        val json = JsonObject().apply {
            addProperty("configId", fakeConfigId)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing lastSynced")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { lastSynced explicit null }`(
        @StringForgery fakeConfigId: String,
        @StringForgery fakeSyncId: String
    ) {
        // Given
        val json = JsonObject().apply {
            addProperty("configId", fakeConfigId)
            add("lastSynced", JsonNull.INSTANCE)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing lastSynced")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { syncId missing }`(
        @StringForgery fakeConfigId: String,
        @LongForgery fakeLastSynced: Long
    ) {
        // Given
        val json = JsonObject().apply {
            addProperty("configId", fakeConfigId)
            addProperty("lastSynced", fakeLastSynced)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing syncId")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { syncId explicit null }`(
        @StringForgery fakeConfigId: String,
        @LongForgery fakeLastSynced: Long
    ) {
        // Given
        val json = JsonObject().apply {
            addProperty("configId", fakeConfigId)
            addProperty("lastSynced", fakeLastSynced)
            add("syncId", JsonNull.INSTANCE)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).contains("missing syncId")
    }

    @Test
    fun `M throw JsonParseException W fromJsonObject() { required field holds a json object }`(
        @LongForgery fakeLastSynced: Long,
        @StringForgery fakeSyncId: String
    ) {
        // Given — a required field holding a non-primitive value (neither absent nor JSON null)
        val json = JsonObject().apply {
            add("configId", JsonObject())
            addProperty("lastSynced", fakeLastSynced)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val exception = assertThrows<JsonParseException> {
            RemoteConfigSyncMetadata.fromJsonObject(json)
        }

        // Then
        assertThat(exception.message).isEqualTo("Unable to parse json into type RemoteConfigSyncMetadata")
    }

    @Test
    fun `M parse successfully W fromJsonObject() { optional fields absent }`(
        @StringForgery fakeConfigId: String,
        @LongForgery fakeLastSynced: Long,
        @StringForgery fakeSyncId: String
    ) {
        // Given — versionId, lastModified and firstApplied keys are entirely absent, not just null
        val json = JsonObject().apply {
            addProperty("configId", fakeConfigId)
            addProperty("lastSynced", fakeLastSynced)
            addProperty("syncId", fakeSyncId)
        }

        // When
        val result = RemoteConfigSyncMetadata.fromJsonObject(json)

        // Then
        assertThat(result.configId).isEqualTo(fakeConfigId)
        assertThat(result.lastSynced).isEqualTo(fakeLastSynced)
        assertThat(result.syncId).isEqualTo(fakeSyncId)
        assertThat(result.versionId).isNull()
        assertThat(result.lastModified).isNull()
        assertThat(result.firstApplied).isNull()
    }
}
