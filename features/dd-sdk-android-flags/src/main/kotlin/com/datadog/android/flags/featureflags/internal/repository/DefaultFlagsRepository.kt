/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.FlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.NoOpFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.internal.repository.store.FlagsStoreManager
import com.datadog.android.flags.featureflags.internal.repository.store.NoOpStoreManager
import com.datadog.android.flags.featureflags.internal.repository.store.StoreManager
import com.datadog.android.flags.internal.model.FlagsContext
import org.json.JSONObject
import java.util.concurrent.ExecutorService

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val executorService: ExecutorService,
    private val flagsContext: FlagsContext,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private var flagsStoreManager: StoreManager = NoOpStoreManager(),
    private val precomputeMapper: PrecomputeMapper = PrecomputeMapper(
        internalLogger = internalLogger
    ),
    private var flagsNetworkManager: FlagsNetworkManager = NoOpFlagsNetworkManager(),
) : FlagsRepository {
    init {
        flagsStoreManager = FlagsStoreManager(
            internalLogger = internalLogger
        )

        flagsNetworkManager = DefaultFlagsNetworkManager(
            internalLogger = internalLogger,
            flagsContext = flagsContext
        )

        fetchFromRemote()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return flagsStoreManager.getBooleanValue(key, defaultValue)
    }

    override fun getString(key: String, defaultValue: String): String {
        return flagsStoreManager.getStringValue(key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return flagsStoreManager.getIntValue(key, defaultValue)
    }

    override fun getDouble(key: String, defaultValue: Double): Double {
        return flagsStoreManager.getDoubleValue(key, defaultValue)
    }

    override fun getJsonObject(key: String, defaultValue: JSONObject): JSONObject {
        return flagsStoreManager.getJsonObjectValue(key, defaultValue)
    }

    private fun fetchFromRemote() {
        executorService.executeSafe(
            operationName = "Fetch precomputed flags",
            internalLogger = internalLogger
        ) {
            val response = flagsNetworkManager.downloadPrecomputedFlags()
            if (response != null) {
                val flagsMap = precomputeMapper.map(response)
                if (flagsMap.isNotEmpty()) {
                    flagsStoreManager.updateFlagsState(flagsMap)
                    cacheToLocalStorage(flagsMap)
                }
            }
        }
    }

    private fun cacheToLocalStorage(flagsMap: Map<String, PrecomputedFlag>) {
        // TODO Implement this
    }

    private fun readFromLocalStorage(): Map<String, PrecomputedFlag> {
        return emptyMap() // TODO: implement this
    }
}
