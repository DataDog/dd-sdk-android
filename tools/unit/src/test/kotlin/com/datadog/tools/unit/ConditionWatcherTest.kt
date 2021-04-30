/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.KotlinAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class ConditionWatcherTest {

    @Test
    fun `M timeout W condition is never satisfied`(forge: Forge) {

        // Given + When + Then
        assertThrows<ConditionWatcher.TimeoutException> {
            ConditionWatcher(pollingIntervalMs = TEST_POLLING_MS) { false }.doWait(
                timeoutMs = TEST_POLLING_MS * forge.anInt(min = 2, max = 5)
            )
        }
    }

    @Test
    fun `M instantly fail W condition throws non-assertion throwable`(forge: Forge) {

        val fakeThrowable = forge.aThrowable()

        // just a coherence check that we have a good throwable in case if underlying aThrowable
        // implementation is changed and includes any sub-class of AssertionError
        assertThat(fakeThrowable).isNotInstanceOf(AssertionError::class.java)

        // Given
        val condition = object : Function0<Boolean> {

            var invocations = 0

            override fun invoke(): Boolean {
                invocations++
                throw fakeThrowable
            }
        }

        // When + Then
        val thrown = assertThrows<Throwable> {
            ConditionWatcher(pollingIntervalMs = TEST_POLLING_MS, condition = condition)
                .doWait(timeoutMs = TEST_POLLING_MS)
        }

        assertThat(thrown).isEqualTo(fakeThrowable)
        assertThat(condition.invocations).isEqualTo(1)
    }

    @Test
    fun `M not instantly fail W condition throws assertion throwable`(forge: Forge) {

        // Given
        val condition = object : Function0<Boolean> {

            var invocations = 0

            override fun invoke(): Boolean {
                invocations++
                assertThat(forge.aNegativeInt()).isGreaterThan(forge.aPositiveInt())
                // make compiler happy, will never reach this line
                return true
            }
        }

        // When + Then
        val exception = assertThrows<ConditionWatcher.TimeoutException> {
            ConditionWatcher(pollingIntervalMs = TEST_POLLING_MS, condition = condition)
                .doWait(
                    timeoutMs = TEST_POLLING_MS * forge.anInt(min = 3, max = 5)
                )
        }

        assertThat(exception.cause).isInstanceOf(AssertionError::class.java)
        assertThat(condition.invocations).isGreaterThan(1)
    }

    @Test
    fun `M passes W condition is satisfied after some time`(forge: Forge) {

        // Given
        val invocationsWhenSatisfied = forge.anInt(min = 3, max = 5)

        // When + Then
        assertDoesNotThrow("Should not throw any exception") {
            ConditionWatcher(
                pollingIntervalMs = TEST_POLLING_MS,
                condition = object : Function0<Boolean> {
                    var invocations = 0

                    override fun invoke(): Boolean {
                        invocations++
                        return invocations >= invocationsWhenSatisfied
                    }
                }
            ).doWait(
                timeoutMs = (invocationsWhenSatisfied + 1) * TEST_POLLING_MS
            )
        }
    }

    private companion object {
        // since it is unit-test we can reduce default polling interval
        const val TEST_POLLING_MS = 50L
    }
}
