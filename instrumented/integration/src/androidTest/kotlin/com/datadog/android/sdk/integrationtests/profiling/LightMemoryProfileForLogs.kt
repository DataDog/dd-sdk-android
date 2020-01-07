package com.datadog.android.sdk.integrationtests.profiling

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.ActivityProfiling
import com.datadog.android.sdk.integrationtests.utils.MemoryProfilingRule
import com.datadog.android.sdk.integrationtests.utils.MockServerRule
import java.io.IOException
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
internal class LightMemoryProfileForLogs {

    @get:Rule
    val mockServerRule = MockServerRule(ActivityProfiling::class.java)

    @get:Rule
    val memoryProfilingRule =
        MemoryProfilingRule()

    // TODO: RUMM-164 Fix the MemoryProfilingRule
    @Test
    @Ignore(
        "This test is very flaky now and we need to find a better" +
                " way to measure the memory"
    )
    fun profileCrashLog() {
        val crash = IOException()
        memoryProfilingRule.profile(action = {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(50) {
                    mockServerRule.activity.logger.d("Test Crash", crash)
                }
            }
        }, threshold = 300)
        /*
           This threshold was determined heuristically for a Pixel 2 device
           running 28 Android API level in Bitrise.
           The idea is to keep the memory consumption under this limit from now on whenever
           we will add more meat to the Log models.
       */
    }
}
