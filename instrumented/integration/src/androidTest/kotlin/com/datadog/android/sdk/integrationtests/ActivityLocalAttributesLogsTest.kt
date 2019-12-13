/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sdk.integrationtests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ActivityLocalAttributesLogsTest :
    ActivityLoggerTest<ActivityLocalAttributesLogs>(ActivityLocalAttributesLogs::class.java) {

    // region ActivityLoggerTest

    override fun expectedMessages(): List<String> {
        return listOf(
            "MainActivity/onCreate",
            "MainActivity/onStart",
            "MainActivity/onResume"
        )
    }

    override fun expectedTags(): List<String> {
        return Runtime.keyValuePairsTags
            .map { "${it.key}:${it.value}".trim(':') }
            .union(Runtime.singleValueTags)
            .toList()
    }

    override fun expectedAttributes(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map.putAll(Runtime.stringAttributes)
        map.putAll(Runtime.intAttribute)
        map.putAll(ActivityLocalAttributesLogs.localAttributes)
        return map
    }

    // endregion
}
