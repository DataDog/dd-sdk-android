/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import java.util.UUID

internal class RumAnonymousIdentifierManager(
    private val dataStore: DataStoreHandler,
    private val core: FeatureSdkCore
) {
    fun manageAnonymousId(shouldTrack: Boolean) {
        if (shouldTrack) {
            handleAnonymousIdTracking(dataStore, core)
        } else {
            clearAnonymousId(dataStore, core)
        }
    }

    private fun handleAnonymousIdTracking(dataStore: DataStoreHandler, core: FeatureSdkCore) {
        dataStore.value(
            ANONYMOUS_ID_KEY,
            null,
            AnonymousIdentifierReadCallback { anonymousId ->
                if (anonymousId == null) {
                    createAndStoreAnonymousId(dataStore, core)
                } else {
                    core.setAnonymousId(anonymousId)
                }
            },
            AnonymousIdentifierDeserializer()
        )
    }

    private fun createAndStoreAnonymousId(dataStore: DataStoreHandler, core: FeatureSdkCore) {
        val newAnonymousId = UUID.randomUUID()
        dataStore.setValue(
            ANONYMOUS_ID_KEY,
            newAnonymousId,
            0,
            null,
            AnonymousIdentifierSerializer()
        )
        core.setAnonymousId(newAnonymousId)
    }

    private fun clearAnonymousId(dataStore: DataStoreHandler, core: FeatureSdkCore) {
        dataStore.removeValue(ANONYMOUS_ID_KEY)
        core.setAnonymousId(null)
    }

    companion object {
        private const val ANONYMOUS_ID_KEY = "anonymous_id_key"
    }
}


@Suppress("SwallowedException")
internal class AnonymousIdentifierDeserializer : Deserializer<String, UUID> {
    override fun deserialize(model: String): UUID? {
        return try {
            UUID.fromString(model)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

internal class AnonymousIdentifierSerializer : Serializer<UUID> {
    override fun serialize(model: UUID): String = model.toString()
}

internal class AnonymousIdentifierReadCallback(
    private val onFinished: (UUID?) -> Unit
) : DataStoreReadCallback<UUID> {
    override fun onSuccess(dataStoreContent: DataStoreContent<UUID>?) {
        onFinished(dataStoreContent?.data)
    }

    override fun onFailure() {
        onFinished(null)
    }
}
