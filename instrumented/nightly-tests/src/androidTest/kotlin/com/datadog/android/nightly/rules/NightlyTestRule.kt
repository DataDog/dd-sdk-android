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
        flushAndShutdownExecutors()
        stopSdk()
        cleanStorageFiles()
    }
}
