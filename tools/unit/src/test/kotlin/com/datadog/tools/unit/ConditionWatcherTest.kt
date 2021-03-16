/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class ConditionWatcherTest {

    @Test
    fun `M timeout W condition is never satisfied`(forge: Forge) {
        assertThrows<ConditionWatcher.TimeoutException> {
            ConditionWatcher { false }.doWait(
                ConditionWatcher.DEFAULT_INTERVAL_MS * forge.anInt(min = 2, max = 5)
            )
        }
    }

    @Test
    fun `M instantly fail W condition throws non-assertion throwable`() {

        val condition = object : Function0<Boolean> {

            var invocations = 0

            override fun invoke(): Boolean {
                invocations++
                throw RuntimeException()
            }
        }

        assertThrows<RuntimeException> {
            ConditionWatcher(condition = condition)
                .doWait(timeoutMs = ConditionWatcher.DEFAULT_INTERVAL_MS)
        }

        assertThat(condition.invocations).isEqualTo(1)
    }

    @Test
    fun `M not instantly fail W condition throws assertion throwable`(forge: Forge) {

        val condition = object : Function0<Boolean> {

            var invocations = 0

            override fun invoke(): Boolean {
                invocations++
                throw AssertionError()
            }
        }

        val exception = assertThrows<ConditionWatcher.TimeoutException> {
            ConditionWatcher(condition = condition).doWait(
                ConditionWatcher.DEFAULT_INTERVAL_MS * forge.anInt(min = 3, max = 5)
            )
        }

        assertThat(condition.invocations).isGreaterThan(1)

        assertThat(exception.cause).isExactlyInstanceOf(AssertionError::class.java)
    }

    @Test
    fun `M passes W condition is satisfied after some time`(forge: Forge) {

        val invocationsUntilSwitch = forge.anInt(min = 3, max = 5)

        ConditionWatcher(
            condition = object : Function0<Boolean> {
                var invocations = 0

                override fun invoke(): Boolean {
                    invocations++
                    return invocations >= invocationsUntilSwitch
                }
            }
        ).doWait((invocationsUntilSwitch + 1) * ConditionWatcher.DEFAULT_INTERVAL_MS)
    }
}
