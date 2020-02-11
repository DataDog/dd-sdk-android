package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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

    lateinit var threadName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        threadName = forge.anAlphabeticalString()
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        underTest = DeferredWriter(
            threadName,
            mockDelegate,
            mockDataMigrator
        )
        underTest.deferredHandler = mockDeferredHandler
        // force the lazy handler thread to consume all the queued messages
        underTest.invokeMethod("consumeQueue")
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
    fun `if no data migrator provided will not perform the migration step`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        underTest = DeferredWriter(
            threadName,
            mockDelegate
        )
        // when
        underTest.write(model)

        // then
        verify(mockDeferredHandler, times(1)).handle(any())
        verifyNoMoreInteractions(mockDeferredHandler)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `run delegate in deferred handler when writing a model`(forge: Forge) {
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

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `run delegate in deferred handler when writing a models list`(forge: Forge) {
        val models: List<String> = forge.aList(size = 10) { forge.anAlphabeticalString() }
        var runnable = Runnable {}
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            runnable = it.arguments[0] as Runnable
            Unit
        }

        underTest.write(models)
        verifyZeroInteractions(mockDelegate)

        runnable.run()
        verify(mockDelegate).write(models)
    }
}
