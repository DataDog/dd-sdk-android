/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import com.datadog.android.core.integration.tests.utils.MockWebServerWrapper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.mockito.internal.matchers.Any
import java.util.concurrent.atomic.AtomicBoolean

abstract class MockServerTest : BaseTest() {

    fun getMockServerWrapper(): MockWebServerWrapper {
        return mockServerWrapper
    }

    companion object {
        private val mockServerStarted = AtomicBoolean(false)
        private val MOCK_SERVER_LOCK = Any()
        protected var mockServerWrapper: MockWebServerWrapper = MockWebServerWrapper()

        @BeforeClass
        @JvmStatic
        fun setupTestSuite() {
            if (mockServerStarted.compareAndSet(false, true)) {
                synchronized(MOCK_SERVER_LOCK) {
                    mockServerWrapper.start()
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            if (mockServerStarted.compareAndSet(true, false)) {
                // we reinitialize the mock server as it cannot be reused
                synchronized(MOCK_SERVER_LOCK) {
                    mockServerWrapper.shutdown()
                    mockServerWrapper = MockWebServerWrapper()
                }
            }
        }
    }
}
