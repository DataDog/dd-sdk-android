package com.datadog.android.core.internal.utils

import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.utils.extension.EnableLogcat
import com.datadog.android.utils.extension.EnableLogcatExtension
import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(EnableLogcatExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
class RuntimeUtilsTest {

    lateinit var verbose: String
    lateinit var debug: String
    lateinit var info: String
    lateinit var warning: String
    lateinit var error: String
    lateinit var wtf: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        verbose = forge.anAlphaNumericalString()
        debug = forge.anAlphaNumericalString()
        info = forge.anAlphaNumericalString()
        warning = forge.anAlphaNumericalString()
        error = forge.anAlphaNumericalString()
        wtf = forge.anAlphaNumericalString()
    }

    @Test
    fun `the sdk logger should always be disabled for remote logs`() {
        if (BuildConfig.LOGCAT_ENABLED) {
            val handler: LogcatLogHandler = sdkLogger.getFieldValue("handler")
            assertThat(handler.serviceName).isEqualTo(SDK_LOG_PREFIX)
        }
    }

    @Test
    @EnableLogcat(isEnabled = false)
    fun `the sdk logger should disable the logcat logs if the BuildConfig flag is false`() {
        val logger = buildSdkLogger()
        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `the sdk logger should enable the logcat logs if the BuildConfig flag is true`() {
        val logger = buildSdkLogger()
        val handler: LogcatLogHandler = logger.getFieldValue("handler")
        assertThat(handler.serviceName).isEqualTo(SDK_LOG_PREFIX)
    }

    @Test
    fun `devLogger should only print verbose or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.VERBOSE)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "V/Datadog: $verbose\n" +
                    "D/Datadog: $debug\n" +
                    "I/Datadog: $info\n" +
                    "W/Datadog: $warning\n" +
                    "E/Datadog: $error\n" +
                    "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should only print debug or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.DEBUG)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "D/Datadog: $debug\n" +
                    "I/Datadog: $info\n" +
                    "W/Datadog: $warning\n" +
                    "E/Datadog: $error\n" +
                    "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should only print info or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.INFO)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "I/Datadog: $info\n" +
                    "W/Datadog: $warning\n" +
                    "E/Datadog: $error\n" +
                    "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should only print warning or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.WARN)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "W/Datadog: $warning\n" +
                    "E/Datadog: $error\n" +
                    "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should only print error or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.ERROR)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "E/Datadog: $error\n" +
                    "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should only print assert or above`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.ASSERT)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEqualTo(
                "A/Datadog: $wtf\n"
            )
        assertThat(errorStream.toString())
            .isEmpty()
    }

    @Test
    fun `devLogger should print nothing`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        Datadog.setVerbosity(Log.ASSERT + 1)

        devLogger.v(verbose)
        devLogger.d(debug)
        devLogger.i(info)
        devLogger.w(warning)
        devLogger.e(error)
        devLogger.wtf(wtf)

        assertThat(outputStream.toString())
            .isEmpty()
        assertThat(errorStream.toString())
            .isEmpty()
    }
}
