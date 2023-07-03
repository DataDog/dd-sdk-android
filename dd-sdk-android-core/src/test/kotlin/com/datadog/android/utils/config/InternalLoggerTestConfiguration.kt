/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.unboundInternalLogger
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock

internal class InternalLoggerTestConfiguration : TestConfiguration {

    lateinit var mockInternalLogger: InternalLogger

    private lateinit var originalInternalLogger: InternalLogger

    override fun setUp(forge: Forge) {
        mockInternalLogger = mock()

        originalInternalLogger = unboundInternalLogger

        unboundInternalLogger = mockInternalLogger
    }

    override fun tearDown(forge: Forge) {
        unboundInternalLogger = originalInternalLogger
    }
}
