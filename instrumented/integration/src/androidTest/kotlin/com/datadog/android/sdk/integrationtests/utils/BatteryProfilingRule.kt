package com.datadog.android.sdk.integrationtests.utils

import android.content.Context.BATTERY_SERVICE
import android.os.BatteryManager
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat

internal class BatteryProfilingRule :
    AbstractProfilingRule<Int>() {

    override fun before() {
    }

    override fun after() {
    }

    override fun measureBeforeAction(): Int {
        return batteryLevel()
    }

    override fun measureAfterAction(): Int {
        return batteryLevel()
    }

    override fun compareWithThreshold(before: Int, after: Int, threshold: Int) {
        val difference = before - after
        assertThat(difference)
            .withFailMessage(
                "We were expecting a difference in battery consumption " +
                        "less than or equal to $threshold." +
                        " Instead we had $difference"
            )
            .isLessThanOrEqualTo(threshold)
    }

    private fun batteryLevel(): Int {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
