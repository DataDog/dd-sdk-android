package com.datadog.android.sdk.integrationtests.profiling

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.ActivityProfiling
import com.datadog.android.sdk.integrationtests.utils.MemoryProfilingRule
import com.datadog.android.sdk.integrationtests.utils.MockServerRule
import java.io.IOException
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

    @Test
    fun profileCrashLog() {
        val crash = IOException()
        memoryProfilingRule.profile({
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(50) {
                    mockServerRule.activity.logger.d("Test Crash", crash)
                }
            }
        }, 50)
    }
}
