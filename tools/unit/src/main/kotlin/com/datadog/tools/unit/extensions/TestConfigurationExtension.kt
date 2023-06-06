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
        val methods = context.requiredTestClass
            .methods
            .filter {
                it.isAnnotationPresent(TestConfigurationsProvider::class.java)
            }

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
        context?.callTestConfigurations { forge -> tearDown(forge) }
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
        operation: TestConfiguration.(Forge) -> Unit
    ) {
        val forge = getForge()
        checkNotNull(forge) { "Unable to get the active Forge instance from the global store." }
        callTestConfigurations(forge, operation)
    }

    @SuppressLint("NewApi")
    private fun ExtensionContext.callTestConfigurations(
        forge: Forge,
        operation: TestConfiguration.(Forge) -> Unit
    ) {
        testConfigurations[uniqueId]?.forEach {
            it.operation(forge)
        }

        if (parent.isPresent) {
            parent.get().callTestConfigurations(forge, operation)
        }
    }

    @SuppressLint("NewApi")
    private fun ExtensionContext.getForge(): Forge? {
        return ForgeExtension.getForge(this)
    }

    // endregion
}
