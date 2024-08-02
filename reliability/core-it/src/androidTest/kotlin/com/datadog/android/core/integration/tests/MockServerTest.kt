/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import com.datadog.android.core.integration.tests.utils.MockWebServerWrapper
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.concurrent.atomic.AtomicBoolean

abstract class MockServerTest : BaseTest() {

    fun getMockServerWrapper(): MockWebServerWrapper {
        return mockServerWrapper
    }

    companion object {
        private val mockServerStarted = AtomicBoolean(false)
        protected val mockServerWrapper: MockWebServerWrapper = MockWebServerWrapper()

        @BeforeClass
        @JvmStatic
        fun setupTestSuite() {
            if (mockServerStarted.compareAndSet(false, true)) {
                mockServerWrapper.start()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            if (mockServerStarted.compareAndSet(true, false)) {
                mockServerWrapper.shutdown()
            }
        }
    }
}
