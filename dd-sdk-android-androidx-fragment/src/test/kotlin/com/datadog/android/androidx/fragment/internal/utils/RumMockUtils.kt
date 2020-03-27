package com.datadog.android.androidx.fragment.internal.utils

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.mock
import java.util.concurrent.atomic.AtomicBoolean

fun mockRumMonitor(): RumMonitor {
    val monitor: RumMonitor = mock()
    GlobalRum.registerIfAbsent(monitor)
    return monitor
}

fun resetRumMonitorToDefaults() {
    val noOpMonitor = createInstance(
        "com.datadog.android.rum.internal.monitor.NoOpRumMonitor"
    )
    GlobalRum::class.java.setStaticValue("monitor", noOpMonitor)
    val isRegistered: AtomicBoolean =
        GlobalRum::class.java.getStaticValue("isRegistered")
    isRegistered.set(false)
}
