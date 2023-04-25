/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import android.annotation.SuppressLint
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.Method

/**
 * A JUnit Jupiter extension that can ensure a test configuration state is properly handled with
 * self contained independent configurations.
 */
class TestConfigurationExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterAllCallback {

    private val testConfigurations: MutableMap<String, List<TestConfiguration>> = mutableMapOf()

    // region BeforeAllCallback

    /** @inheritdoc */
    override fun beforeAll(context: ExtensionContext?) {
        checkNotNull(context)
        val methods = mutableListOf<Method>()
        collectProviderMethods(context.requiredTestClass, methods)

        val configs = methods.flatMap { it.invoke(null) as List<*> }
            .filterIsInstance<TestConfiguration>()
        if (configs.isNotEmpty()) {
            testConfigurations[context.uniqueId] = configs
        }
    }

    // endregion

    // region BeforeEachCallback

    /** @inheritdoc */
    override fun beforeEach(context: ExtensionContext?) {
        context?.callTestConfigurations { forge -> setUp(forge) }
    }

    // endregion

    // region AfterEachCallback

    /** @inheritdoc */
    override fun afterEach(context: ExtensionContext?) {
        // reverse here is needed, because of the following example:
        // say there is test class A, and test class B : A.
        // both declare InternalLoggerTestExtension, which saves original logger in setUp
        // and restores it during tearDown.
        // that means that during setup we will save original logger in B, and then mocked
        // logger in A. But during tear down we will restore first original logger in B, and then
        // mocked logger in A if we don't reverse the call order. So reversing the order to follow
        // the same call sequence as JUnit is doing.
        context?.callTestConfigurations(reverseCallOrder = true) { forge -> tearDown(forge) }
    }

    // endregion

    // region AfterAllCallback

    /** @inheritdoc */
    override fun afterAll(context: ExtensionContext?) {
        checkNotNull(context)
        testConfigurations.remove(context.uniqueId)
    }

    // endregion

    // region Internal

    @Suppress("CheckInternal") // not an issue in unit tests
    private fun ExtensionContext.callTestConfigurations(
        reverseCallOrder: Boolean = false,
        operation: TestConfiguration.(Forge) -> Unit
    ) {
        val forge = getForge()
        checkNotNull(forge) { "Unable to get the active Forge instance from the global store." }
        callTestConfigurations(forge, reverseCallOrder, operation)
    }

    @SuppressLint("NewApi")
    private fun ExtensionContext.callTestConfigurations(
        forge: Forge,
        reverseCallOrder: Boolean,
        operation: TestConfiguration.(Forge) -> Unit
    ) {
        val configs = testConfigurations[uniqueId]?.let {
            if (reverseCallOrder) {
                it.reversed()
            } else {
                it
            }
        }
        configs?.forEach {
            it.operation(forge)
        }

        if (parent.isPresent) {
            parent.get().callTestConfigurations(forge, reverseCallOrder, operation)
        }
    }

    @SuppressLint("NewApi")
    private fun ExtensionContext.getForge(): Forge? {
        return ForgeExtension.getForge(this)
    }

    private fun collectProviderMethods(clazz: Class<*>, accumulator: MutableList<Method>) {
        // GesturesListenerScrollSwipeTest <- child of abstract AbstractGesturesListenerTest.
        // Only AbstractGesturesListenerTest has getTestConfigurations.
        // GesturesListenerScrollSwipeTest::class.java.methods returns only parent one, as expected.
        // So to solve this we will use declaredMethods with parent traversal.
        accumulator += clazz.declaredMethods
            .filter {
                it.isAnnotationPresent(TestConfigurationsProvider::class.java)
            }
        if (clazz.superclass != null) {
            collectProviderMethods(clazz.superclass, accumulator)
        }
    }

    // endregion
}
