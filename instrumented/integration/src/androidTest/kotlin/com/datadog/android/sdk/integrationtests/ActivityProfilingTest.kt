package com.datadog.android.sdk.integrationtests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.utils.MemoryProfilingDatadogRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException


@LargeTest
@RunWith(AndroidJUnit4::class)
class ActivityProfilingTest {

    @get:Rule
    val memoryProfilingDatadogRule =
        MemoryProfilingDatadogRule(ActivityProfiling::class.java)

    @Test
    fun profileCrashLog() {
        val crash = IOException()
        memoryProfilingDatadogRule.profileForMemoryConsumption({
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(50) {
                    memoryProfilingDatadogRule.activity.logger.d("Test Crash", crash)
                }
            }
        })
    }

    @Test
    fun profileCrashLogWithAttributes() {
        val crash = IOException()
        val attributes = mutableMapOf<String, String>()
        for (i in 0..100) {
            attributes["key$i"] = "value$i"
        }

        memoryProfilingDatadogRule.profileForMemoryConsumption({
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(50) {
                    memoryProfilingDatadogRule.activity.logger.d(
                        "Test Crash",
                        crash,
                        attributes = attributes
                    )
                }
            }
        })
    }

}