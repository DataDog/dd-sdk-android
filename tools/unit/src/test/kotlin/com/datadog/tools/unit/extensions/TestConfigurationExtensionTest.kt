/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(TestConfigurationExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class TestConfigurationExtensionTest {

    @Test
    fun testSomething() {
        assertThat(setUpCalled).isEqualTo(1)
        assertThat(tearDownCalled).isEqualTo(0)
    }

    companion object {

        var setUpCalled: Int = 0
        var tearDownCalled: Int = 0

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            assertThat(setUpCalled).isEqualTo(0)
            assertThat(tearDownCalled).isEqualTo(0)
        }

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            assertThat(setUpCalled).isEqualTo(1)
            assertThat(tearDownCalled).isEqualTo(1)
        }

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(
                object : TestConfiguration {
                    override fun setUp(forge: Forge) {
                        setUpCalled++
                    }

                    override fun tearDown(forge: Forge) {
                        tearDownCalled++
                    }
                }
            )
        }
    }
}
