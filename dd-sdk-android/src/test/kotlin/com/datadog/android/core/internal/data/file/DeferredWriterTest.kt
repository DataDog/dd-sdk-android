package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings
internal class DeferredWriterTest {

    lateinit var underTest: DeferredWriter<String>
    @Mock
    lateinit var mockDelegate: Writer<String>
    @Mock
    lateinit var mockDeferredHandler: AndroidDeferredHandler

    @Mock
    lateinit var mockDataMigrator: DataMigrator

    @BeforeEach
    fun `set up`() {
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        underTest = DeferredWriter(
            mockDataMigrator,
            mockDelegate
        )
        underTest.deferredHandler = mockDeferredHandler
        underTest.invokeMethod(
            "consumeQueue",
            methodEnclosingClass = LazyHandlerThread::class.java
        ) // force the lazy handler thread to consume all the queued messages
    }

    @Test
    fun `migrates the data before doing anything else`(forge: Forge) {
        val model = forge.anAlphabeticalString()

        // when
        underTest.write(model)

        // then
        val inOrder = inOrder(mockDataMigrator, mockDelegate)
        inOrder.verify(mockDataMigrator).migrateData()
        inOrder.verify(mockDelegate).write(model)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `run delegate in deferred handler`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        var runnable = Runnable {}
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            runnable = it.arguments[0] as Runnable
            Unit
        }

        underTest.write(model)
        verifyZeroInteractions(mockDelegate)

        runnable.run()
        verify(mockDelegate).write(model)
    }
}
