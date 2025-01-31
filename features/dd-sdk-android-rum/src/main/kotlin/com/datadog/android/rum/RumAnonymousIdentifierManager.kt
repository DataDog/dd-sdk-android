/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import java.util.UUID

object RumAnonymousIdentifierManager {
    @JvmStatic
    fun manageAnonymousId(shouldTrack: Boolean, dataStore: DataStoreHandler, core: FeatureSdkCore) {
        val anonymousIdKey = "anonymous_id_key"
        if (shouldTrack) {
            dataStore.value(
                anonymousIdKey,
                null,
                AnonymousIdentifierReadCallback { anonymousId ->
                    if (anonymousId == null) {
                        val newAnonymousId = UUID.randomUUID()
                        dataStore.setValue(
                            anonymousIdKey,
                            newAnonymousId,
                            0,
                            null,
                            AnonymousIdentifierSerializer()
                        )
                        core.setAnonymousId(newAnonymousId)
                    } else {
                        core.setAnonymousId(anonymousId)
                    }
                },
                AnonymousIdentifierDeserializer()
            )
        } else {
            dataStore.removeValue(anonymousIdKey)
            core.setAnonymousId(null)
        }
    }
}

internal class AnonymousIdentifierDeserializer : Deserializer<String, UUID> {
    override fun deserialize(model: String): UUID? = UUID.fromString(model)
}

internal class AnonymousIdentifierSerializer : Serializer<UUID> {
    override fun serialize(model: UUID): String? = model.toString()
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
