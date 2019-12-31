package com.datadog.android.sdk.integrationtests.utils

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal abstract class AbstractProfilingRule<T> : TestRule {

    abstract fun before()

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                before()
                base?.evaluate()
                after()
            }
        }
    }

    abstract fun after()

    abstract fun measureBeforeAction(): T
    abstract fun measureAfterAction(): T
    abstract fun compareWithThreshold(before: T, after: T, threshold: T)

    fun profile(action: () -> Unit, threshold: T, warmupAction: () -> Unit = {}) {
        warmupAction()
        val before = measureBeforeAction()
        action()
        val after = measureAfterAction()
        compareWithThreshold(before, after, threshold)
    }
}
