/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.config

import com.datadog.android.Datadog
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.DatadogCore
import com.datadog.android.internal.telemetry.NetworkInstrumentationConfiguration
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Answers
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DatadogSingletonTestConfiguration :
    MockTestConfiguration<InternalSdkCore>(InternalSdkCore::class.java) {

    lateinit var mockNetworkConfiguration: NetworkInstrumentationConfiguration

    override fun setUp(forge: Forge) {
        mockNetworkConfiguration = mock()
        mockInstance = createMockSdkCore(mockNetworkConfiguration)
        Datadog.registry.register(null, mockInstance)
    }

    override fun tearDown(forge: Forge) {
        clearRegistry()
        super.tearDown(forge)
    }

    fun register(sdkCore: InternalSdkCore, name: String?) {
        Datadog.registry.register(name, sdkCore)
    }

    fun clearRegistry() {
        Datadog.registry.clear()
    }
}

// Exercises concrete-core proxy access while allowing lifecycle tests to delegate feature behavior.
fun createMockSdkCore(
    networkConfiguration: NetworkInstrumentationConfiguration,
    delegate: InternalSdkCore? = null
): InternalSdkCore {
    val mockCore = mock<DatadogCore>(defaultAnswer = delegate?.let { delegatesTo<Any>(it) } ?: Answers.RETURNS_DEFAULTS)
    doReturn(true).whenever(mockCore).isActive
    doReturn(networkConfiguration).whenever(mockCore).networkInstrumentationConfiguration
    return mockCore
}
