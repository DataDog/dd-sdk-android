/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import android.text.format.DateUtils
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.sessionreplay.model.ResourceHashesEntry
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class ResourceDataStoreManager(
    private val featureSdkCore: FeatureSdkCore,
    private val resourceHashesSerializer: Serializer<ResourceHashesEntry>,
    private val resourceHashesDeserializer: Deserializer<String, ResourceHashesEntry>
) {
    @Suppress("UnsafeThirdPartyFunctionCall") // map is initialized empty
    private val knownResources = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val storedLastUpdateDateNs = AtomicLong(System.nanoTime())
    private val isInitialized = AtomicBoolean(false) // has init finished executing its async actions

    init {
        fetchStoredResourceHashes(
            onFetchSuccessful = lambda@{ storedEntry ->
                val storedData = storedEntry?.data

                if (storedData == null) {
                    finishedInitializingManager()
                    return@lambda
                }

                val lastUpdateDateNs = storedData.lastUpdateDateNs.toLong()
                val storedHashes = storedData.resourceHashes

                if (didDataStoreExpire(lastUpdateDateNs)) {
                    deleteStoredHashesEntry(
                        callback = object : DataStoreWriteCallback {
                            override fun onSuccess() {
                                finishedInitializingManager()
                            }

                            override fun onFailure() {
                                finishedInitializingManager()
                            }
                        }
                    )
                } else {
                    storedLastUpdateDateNs.set(lastUpdateDateNs)
                    knownResources.addAll(storedHashes)
                    finishedInitializingManager()
                }
            },
            onFetchFailure = {
                finishedInitializingManager()
            }
        )
    }

    internal fun isPreviouslySentResource(resourceHash: String): Boolean =
        knownResources.contains(resourceHash)

    internal fun cacheResourceHash(resourceHash: String) {
        knownResources.add(resourceHash)
        writeResourcesToStore()
    }

    internal fun isReady(): Boolean =
        isInitialized.get()

    // region internal

    private fun finishedInitializingManager() {
        isInitialized.set(true)
    }

    private fun writeResourcesToStore() {
        val data = ResourceHashesEntry(
            lastUpdateDateNs = storedLastUpdateDateNs,
            resourceHashes = knownResources.toList()
        )

        featureSdkCore.getFeature(
            Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME
        )?.dataStore?.setValue(
            data = data,
            key = DATASTORE_HASHES_ENTRY_NAME,
            serializer = resourceHashesSerializer
        )
    }

    private fun fetchStoredResourceHashes(
        onFetchSuccessful: (dataStoreContent: DataStoreContent<ResourceHashesEntry>?) -> Unit,
        onFetchFailure: () -> Unit
    ) {
        featureSdkCore.getFeature(
            Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME
        )?.dataStore?.value(
            key = DATASTORE_HASHES_ENTRY_NAME,
            deserializer = resourceHashesDeserializer,
            callback = object : DataStoreReadCallback<ResourceHashesEntry> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ResourceHashesEntry>?) {
                    onFetchSuccessful(dataStoreContent)
                }

                override fun onFailure() {
                    onFetchFailure()
                }
            }
        )
    }

    private fun deleteStoredHashesEntry(callback: DataStoreWriteCallback) =
        featureSdkCore.getFeature(
            Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME
        )?.dataStore?.removeValue(
            key = DATASTORE_HASHES_ENTRY_NAME,
            callback = callback
        )

    private fun didDataStoreExpire(lastUpdateDate: Long): Boolean =
        System.nanoTime() - lastUpdateDate > DATASTORE_EXPIRATION_NS

    // endregion

    internal companion object {
        internal const val DATASTORE_EXPIRATION_NS =
            DateUtils.DAY_IN_MILLIS * 30 * 1000 * 1000 // 30 days in nanoseconds
        internal const val DATASTORE_HASHES_ENTRY_NAME = "resource-hash-store"
    }
}
