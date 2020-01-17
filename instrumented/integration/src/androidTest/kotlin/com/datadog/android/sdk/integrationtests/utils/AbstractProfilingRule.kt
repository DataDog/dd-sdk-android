package com.datadog.android.sdk.integrationtests.utils

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal abstract class AbstractProfilingRule<T> : TestRule {

    abstract fun before()

    val noOpWarmUpFunction = {}

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

    open fun doBeforeWarmUp() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    open fun doBeforeAction() {}
    open fun doAfterWarmUp() {}
    open fun doAfterAction() {}
    abstract fun measureBeforeAction(): T
    abstract fun measureAfterAction(): T
    abstract fun compareWithThreshold(before: T, after: T, threshold: T)

    fun profile(
        warmupAction: () -> Unit = noOpWarmUpFunction,
        action: () -> Unit,
        threshold: T,
        repeatCount: Int = 64
    ) {
        doBeforeWarmUp()
        warmupAction()
        doAfterWarmUp()
        val before = measureBeforeAction()
        doBeforeAction()
        action()
        doAfterAction()
        val after = measureAfterAction()
        compareWithThreshold(before, after, threshold)
    }

    fun repeatOnMainThread(
        occurrences: Int,
        durationMs: Long,
        minimumSleepMs: Long,
        action: () -> Unit
    ): () -> Unit {
        val periodMs = durationMs / occurrences
        val sleepMs = Math.max(minimumSleepMs, periodMs)
        val repeatPerIteration = sleepMs / periodMs
        val iterations = durationMs / sleepMs
        val countDownLatch = CountDownLatch(1)
        return {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                for (i in 0..iterations) {
                    repeat(repeatPerIteration.toInt()) { action() }
                    Thread.sleep(sleepMs)
                }
                countDownLatch.countDown()
            }
            countDownLatch.await(
                durationMs + COUNTDOWN_LATCH_TIMEOUT_EXTRA_MS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    companion object {
        const val COUNTDOWN_LATCH_TIMEOUT_EXTRA_MS = 3000L // 3 extra seconds
    }
}
