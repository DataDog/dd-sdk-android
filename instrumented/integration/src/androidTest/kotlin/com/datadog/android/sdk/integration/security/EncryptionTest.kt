/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.security

import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.SecurityConfig
import com.datadog.android.log.Logger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.security.Encryption
import com.datadog.android.tracing.AndroidTracer
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.junit4.ForgeRule
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.inv
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
internal class EncryptionTest {

    @get:Rule
    val forge = ForgeRule()

    @Before
    fun setUp() = deleteAllSdkFiles()

    @After
    fun tearDown() = deleteAllSdkFiles()

    @Test
    fun must_encrypt_all_data() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val configuration = createSdkConfiguration()
        val credentials = createCredentials()

        Datadog.initialize(targetContext, credentials, configuration, TrackingConsent.PENDING)

        val rumMonitor = RumMonitor.Builder().build()
        GlobalRum.registerIfAbsent(rumMonitor)

        val tracer = AndroidTracer.Builder().setBundleWithRumEnabled(true).build()
        GlobalTracer.registerIfAbsent(tracer)

        val logger = Logger.Builder()
            .setBundleWithRumEnabled(true)
            .setBundleWithTraceEnabled(true)
            .build()

        sendEventsForAllFeatures(rumMonitor, logger, tracer)

        flushAndShutdownExecutors()
        stopSdk()

        // this part is flaky, because we make an assumption on the internal folder/file structure
        val isPendingDirectory = { file: File ->
            file.name.contains("pending")
        }
        val isNdkPendingDirectory = { file: File ->
            file.name == "ndk_crash_reports_intermediary_v2"
        }

        val dataDirectories =
            targetContext.cacheDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("datadog") }
                ?.flatMap { it.listFiles()?.asIterable() ?: emptyList() }
                ?.filter {
                    if (it.isDirectory) {
                        (isPendingDirectory(it) && !it.name.contains("crash")) ||
                            isNdkPendingDirectory(it)
                    } else {
                        false
                    }
                }!!

        assertThat(dataDirectories).isNotEmpty()

        dataDirectories.forEach { directory ->

            val files = directory.listFiles()!!

            assertThat(files)
                .overridingErrorMessage("Expecting ${directory.path} to contain files")
                .isNotEmpty()

            files.forEach { file ->
                val content = file.readText()

                assertThat(content)
                    .overridingErrorMessage("Expecting ${file.path} to not contain plain text")
                    .doesNotContain("source")
                assertThat(content)
                    .overridingErrorMessage("Expecting ${file.path} to contain encryption marker")
                    .contains(ENCRYPTION_MARKER)
            }
        }
    }

    // region private

    private fun createCredentials(): Credentials {
        return Credentials(
            clientToken = forge.anAlphaNumericalString(),
            envName = forge.anAlphaNumericalString(),
            variant = Credentials.NO_VARIANT,
            rumApplicationId = forge.anAlphaNumericalString()
        )
    }

    private fun createSdkConfiguration(): Configuration {
        val encryption = object : Encryption {
            override fun encrypt(data: ByteArray): ByteArray {
                return ENCRYPTION_MARKER.toByteArray() + data.map { it.inv() }
            }

            override fun decrypt(data: ByteArray): ByteArray {
                throw IllegalStateException("Shouldn't be called in this test")
            }
        }

        return Configuration
            .Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true,
                sessionReplayEnabled = true
            )
            .setSecurityConfig(SecurityConfig(localDataEncryption = encryption))
            .build()
    }

    private fun sendEventsForAllFeatures(rumMonitor: RumMonitor, logger: Logger, tracer: Tracer) {
        val viewName = "rumView-${forge.aString()}"
        rumMonitor.startView(viewName, viewName)

        logger.w("Started a view")
        val resourceName = "rumResource-${forge.aString()}"

        val span = tracer.buildSpan(resourceName)
            .start()

        rumMonitor.startResource(
            resourceName,
            "GET",
            "https://${forge.anAlphaNumericalString()}.com"
        )

        rumMonitor.addUserAction(
            RumActionType.CUSTOM,
            "rumAction-${forge.aString()}",
            emptyMap()
        )
        logger.w("Action added")

        rumMonitor.stopResource(
            resourceName,
            forge.anInt(100, 600),
            forge.aLong(min = 0L),
            forge.aValueFrom(RumResourceKind::class.java),
            attributes = emptyMap()
        )

        span.finish()

        rumMonitor.stopView(viewName)
    }

    private fun deleteAllSdkFiles() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.cacheDir.listFiles()?.forEach {
            it.deleteRecursively()
        }
    }

    private fun stopSdk() {
        invokeDatadogMethod("stop")
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        val isRumRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRumRegistered.set(false)
    }

    private fun flushAndShutdownExecutors() {
        invokeDatadogMethod("flushAndShutdownExecutors")
    }

    private fun invokeDatadogMethod(method: String) {
        val instance = Datadog.javaClass.getDeclaredField("INSTANCE")
        instance.isAccessible = true
        val callMethod = Datadog.javaClass.declaredMethods.first { it.name.startsWith(method) }
        callMethod.isAccessible = true
        callMethod.invoke(instance.get(null))
    }

    // endregion

    companion object {
        const val ENCRYPTION_MARKER = "~ENCRYPTION-MARKER~"
    }
}
