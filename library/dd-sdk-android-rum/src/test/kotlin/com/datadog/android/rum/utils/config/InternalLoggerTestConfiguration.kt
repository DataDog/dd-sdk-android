/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.config

import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class InternalLoggerTestConfiguration : TestConfiguration {

    lateinit var mockInternalLogger: InternalLogger

    private lateinit var originalInternalLogger: InternalLogger

    override fun setUp(forge: Forge) {
        mockInternalLogger = mock()

        originalInternalLogger = internalLogger

        internalLogger = mockInternalLogger
    }

    override fun tearDown(forge: Forge) {
        internalLogger = originalInternalLogger
    }
}
