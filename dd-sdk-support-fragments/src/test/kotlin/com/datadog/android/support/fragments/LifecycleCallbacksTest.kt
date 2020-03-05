package com.datadog.android.support.fragments

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal abstract class LifecycleCallbacksTest {
    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @BeforeEach
    open fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    open fun `tear down`() {
        val noOpMonitor = createInstance(
            "com.datadog.android.rum.internal.monitor.NoOpRumMonitor"
        )
        GlobalRum::class.java.setStaticValue("monitor", noOpMonitor)
        val isRegistered: AtomicBoolean =
            GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }
}
