package com.datadog.android.sdk.integrationtests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.integrationtests.utils.DDTestRule
import com.datadog.android.sdk.integrationtests.utils.assertj.HeadersAssert
import com.datadog.android.sdk.integrationtests.utils.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.sdk.integrationtests.utils.assertj.LogsListAssert.Companion.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoggerApiE2eBasicTests {

    @get:Rule
    var activityTestRule =
        DDTestRule(MainActivity::class.java)

    @Test
    fun testActivityResumeLogs() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(15 * 1000) // we will wait to make sure all batches are consumed

        // assert that we have all the logs in order
        // assert that each log has predefined attributes
        assertThat(activityTestRule.requestObjects)
            .containsOnlyLogsWithMessagesInOrder(
                "MainActivity/onCreate",
                "MainActivity/onStart",
                "MainActivity/onResume"
            )
            .hasService(InstrumentationRegistry.getInstrumentation().targetContext.packageName)
            .hasAttributes(*Runtime.attributes)
            .hasTags(*Runtime.tags)

        // check the headers
        assertThat(activityTestRule.requestHeaders)
            .hasHeader(HeadersAssert.HEADER_CT, Runtime.DD_CONTENT_TYPE)
            .hasHeader(HeadersAssert.HEADER_UA, activityTestRule.userAgent)
    }
}
