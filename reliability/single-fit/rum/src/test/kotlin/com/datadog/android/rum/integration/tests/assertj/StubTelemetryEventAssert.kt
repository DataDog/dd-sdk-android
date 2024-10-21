/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.android.core.stub.StubTelemetryEvent
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.Gson
import org.assertj.core.api.AbstractObjectAssert

class StubTelemetryEventAssert(actual: StubTelemetryEvent?) :
    AbstractObjectAssert<StubTelemetryEventAssert, StubTelemetryEvent>(actual, StubTelemetryEventAssert::class.java) {

    val additionalPropertiesJson by lazy { Gson().toJsonTree(actual!!.additionalProperties).asJsonObject }

    fun hasWasStopped(wasStopped: Boolean): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.was_stopped", wasStopped)
        return this
    }

    fun hasViewCount(viewCount: Int): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.views_count.total", viewCount)
        return this
    }

    fun hasBackgroundEventsTrackingEnable(enable: Boolean): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.has_background_events_tracking_enabled", enable)
        return this
    }

    fun hasNtpOffsetAtStart(ntpOffsetAtStart: Long): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.ntp_offset.at_start", ntpOffsetAtStart)
        return this
    }

    fun hasNtpOffsetAtEnd(ntpOffsetAtEnd: Long): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.ntp_offset.at_end", ntpOffsetAtEnd)
        return this
    }

    fun hasNoViewActionEventCounts(count: Int): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.no_view_events_count.actions", count)
        return this
    }

    fun hasNoViewErrorEventCounts(count: Int): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.no_view_events_count.errors", count)
        return this
    }

    fun hasNoViewResourceEventCounts(count: Int): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.no_view_events_count.resources", count)
        return this
    }

    fun hasNoViewLongTaskEventCounts(count: Int): StubTelemetryEventAssert {
        assertThat(additionalPropertiesJson, true).hasField("rse.no_view_events_count.long_tasks", count)
        return this
    }

    companion object {
        fun assertThat(actual: StubTelemetryEvent?): StubTelemetryEventAssert {
            return StubTelemetryEventAssert(actual)
        }
    }
}
