package com.datadog.android.core.internal.utils

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.UploadWorker
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockAppContext
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        SystemOutputExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WorkManagerUtilsTest {

    @Mock
    lateinit var mockedWorkManager: WorkManagerImpl

    lateinit var mockAppContext: Application

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockAppContext = mockAppContext()
        Datadog.initialize(mockAppContext, forge.anHexadecimalString())
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will schedule the worker if WorkManager was correctly instantiated`() {
        // given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockedWorkManager)

        // when
        triggerUploadWorker(mockAppContext())

        // then
        verify(mockedWorkManager).enqueueUniqueWork(
            eq(UPLOAD_WORKER_TAG),
            eq(ExistingWorkPolicy.REPLACE),
            argThat<OneTimeWorkRequest> {
                this.workSpec.workerClassName == UploadWorker::class.java.canonicalName
            })
    }

    @Test
    fun `it will handle the exception if WorkManager was not correctly instantiated`(
        @SystemOutStream outStream: ByteArrayOutputStream
    ) {
        // when
        triggerUploadWorker(mockAppContext())

        // then
        verifyZeroInteractions(mockedWorkManager)
        if (BuildConfig.DEBUG) {
            val logMessages = outStream.toString().trim().split("\n")
            assertThat(logMessages[0]).matches("E/DD_LOG: $TAG.*")
        }
    }
}
