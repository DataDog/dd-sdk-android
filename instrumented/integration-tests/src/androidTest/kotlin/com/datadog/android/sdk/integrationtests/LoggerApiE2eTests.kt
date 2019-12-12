package com.datadog.android.sdk.integrationtests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.utils.DDTestRule
import com.datadog.android.sdk.integrationtests.utils.assertj.JsonListAssert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class LoggerApiE2eTests {

    @get:Rule
    var activityTestRule =
        DDTestRule(MainActivity::class.java)


    @Test
    fun testActivityResumeLogs() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(15 * 1000) // we will wait to make sure all batches are consumed

        // assert that we have all the logs in order
        JsonListAssert.assertThat(activityTestRule.requestObjects)
            .hasLogsWithMessagesInOrder(
                "MainActivity/onCreate",
                "MainActivity/onStart",
                "MainActivity/onResume"
            )
    }
}