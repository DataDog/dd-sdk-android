/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rules

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.utils.cleanStorageFiles
import com.datadog.android.nightly.utils.flushAndShutdownExecutors
import com.datadog.android.nightly.utils.stopSdk
import org.junit.rules.ExternalResource

class NightlyTestRule : ExternalResource() {

    override fun after() {
        super.after()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // waitForIdleSync above waits only for idle of the main thread. We need this delay to be
        // able to digest all the events, especially for RUM. Flushing and stopping executors below
        // is not enough, because write logic may be complicated. Imagine resource error in
        // background tracking in RUM:
        // - error is submitted to RUM monitor
        // - we flush and shutdown executors. During this process we process tasks in RUM (error),
        // and process tasks in persistence thread (write this error)
        // - once error is written it will trigger event saying it is written. This event is
        // submitted to RUM (which was already stopped), so that we can send 1st view event
        // (impossible to process - RUM is stopped; impossible to write - persistence executor is stopped)
        Thread.sleep(200)

        flushAndShutdownExecutors()
        stopSdk()
        cleanStorageFiles()
    }
}
