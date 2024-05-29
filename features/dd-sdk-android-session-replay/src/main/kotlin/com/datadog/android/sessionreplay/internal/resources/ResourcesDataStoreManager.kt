/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import androidx.annotation.WorkerThread
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import java.util.Collections

internal class ResourcesDataStoreManager(
    private val featureSdkCore: FeatureSdkCore,
    private val serializer: Serializer<Set<String>>,
    private val deserializer: Deserializer<String, Set<String>>
) {
    private val knownResources: MutableSet<String> by lazy {
        val resourcesMap = mutableSetOf<String>()
        val featureScope = featureSdkCore.getFeature(SESSION_REPLAY_RESOURCES_FEATURE_NAME)

        featureScope?.dataStore?.value(
            key = DATASTORE_FILENAME,
            deserializer = deserializer,
            callback = object : DataStoreCallback<Set<String>> {
                override fun onSuccess(dataStoreContent: DataStoreContent<Set<String>>) {
                    dataStoreContent.data?.let {
                        resourcesMap.addAll(it)
                    }
                }

                override fun onFailure() {
                    return
                }

                override fun onNoData() {
                    return
                }
            }
        )

        Collections.synchronizedSet(resourcesMap)
    }

    @WorkerThread
    internal fun wasResourcePreviouslySent(resourceHash: String): Boolean =
        knownResources.contains(resourceHash)

    @WorkerThread
    @Synchronized
    internal fun store(resourceHash: String) {
        knownResources.add(resourceHash)
        featureSdkCore.getFeature(SESSION_REPLAY_RESOURCES_FEATURE_NAME)
            ?.dataStore?.setValue(
                data = knownResources,
                key = DATASTORE_FILENAME,
                serializer = serializer
            )
    }

    internal companion object {
        internal const val DATASTORE_FILENAME = "resource-hash-store"
    }
}
