package com.datadog.android.sdk.integrationtests.profiling

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.ActivityProfiling
import com.datadog.android.sdk.integrationtests.utils.CpuProfilingRule
import com.datadog.android.sdk.integrationtests.utils.MockServerRule
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
internal class CpuProfileForLogs {

    @get:Rule
    val mockServerRule = MockServerRule(ActivityProfiling::class.java)
    @get:Rule
    val cpuProfilingRule = CpuProfilingRule()

    @Test
    fun profileCrashLogWithLargeNumberOfAttributes() {
        val crash = IOException()
        val attributes = mutableMapOf<String, String>()
        for (i in 0..100) {
            attributes["key$i"] = "value$i"
        }
        val action = {
            mockServerRule.activity.logger.d(
                "Test Crash",
                crash,
                attributes = attributes
            )
        }
        cpuProfilingRule.profile(
            {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    repeat(3) {
                        action()
                        Thread.sleep(20)
                    }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }, cpuProfilingRule.repeatOnMainThread(
                1000,
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.SECONDS.toMillis(1),
                action
            ),
            7.0
        )
    }
}
