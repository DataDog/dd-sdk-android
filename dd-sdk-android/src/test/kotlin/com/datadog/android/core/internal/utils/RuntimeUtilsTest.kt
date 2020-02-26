package com.datadog.android.core.internal.utils

import android.os.Build
import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.utils.extension.EnableLogcat
import com.datadog.android.utils.extension.EnableLogcatExtension
import com.datadog.android.utils.resolveTagName
import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(EnableLogcatExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class),
    ExtendWith(ApiLevelExtension::class)
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

    @AfterEach
    fun `tear down`() {
        Datadog.setFieldValue("isDebug", false)
    }

    @Test
    fun `the sdk logger should always be disabled for remote logs`() {
        if (BuildConfig.LOGCAT_ENABLED) {
            val handler = sdkLogger.getFieldValue<LogHandler>("handler") as LogcatLogHandler
            assertThat(handler.serviceName).isEqualTo(SDK_LOG_PREFIX)
        }
    }

    @Test
    @EnableLogcat(isEnabled = false)
    fun `the sdk logger should disable the logcat logs if the BuildConfig flag is false`() {
        val logger = buildSdkLogger()
        val handler = logger.getFieldValue<LogHandler>("handler")
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `the sdk logger should enable the logcat logs if the BuildConfig flag is true`() {
        val logger = buildSdkLogger()
        val handler = logger.getFieldValue<LogHandler>("handler") as LogcatLogHandler
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
        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "V/$expectedTagName: $verbose\n" +
                        "D/$expectedTagName: $debug\n" +
                        "I/$expectedTagName: $info\n" +
                        "W/$expectedTagName: $warning\n" +
                        "E/$expectedTagName: $error\n" +
                        "A/$expectedTagName: $wtf\n"
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

        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "D/$expectedTagName: $debug\n" +
                        "I/$expectedTagName: $info\n" +
                        "W/$expectedTagName: $warning\n" +
                        "E/$expectedTagName: $error\n" +
                        "A/$expectedTagName: $wtf\n"
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

        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "I/$expectedTagName: $info\n" +
                        "W/$expectedTagName: $warning\n" +
                        "E/$expectedTagName: $error\n" +
                        "A/$expectedTagName: $wtf\n"
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

        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "W/$expectedTagName: $warning\n" +
                        "E/$expectedTagName: $error\n" +
                        "A/$expectedTagName: $wtf\n"
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

        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "E/$expectedTagName: $error\n" +
                        "A/$expectedTagName: $wtf\n"
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

        val expectedTagName = resolveTagName(this)
        assertThat(outputStream.toString())
            .isEqualTo(
                "A/$expectedTagName: $wtf\n"
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

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `devLogger should use whole caller name as tag if inDebug and above N`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {

        // given
        val fakeMessage = forge.anAlphabeticalString()
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.setFieldValue("isDebug", true)
        val underTest = LogCaller()

        // when
        underTest.logMessage(fakeMessage)

        // then
        assertThat(outputStream.toString())
            .isEqualTo(
                "V/RuntimeUtilsTest\$LogCaller: $fakeMessage\n" +
                        "D/RuntimeUtilsTest\$LogCaller: $fakeMessage\n" +
                        "I/RuntimeUtilsTest\$LogCaller: $fakeMessage\n" +
                        "W/RuntimeUtilsTest\$LogCaller: $fakeMessage\n" +
                        "E/RuntimeUtilsTest\$LogCaller: $fakeMessage\n" +
                        "A/RuntimeUtilsTest\$LogCaller: $fakeMessage\n"
            )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `devLogger will cut caller name to max accepted tag length if below N`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {

        // given
        val fakeMessage = forge.anAlphabeticalString()
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.setFieldValue("isDebug", true)
        val underTest = LogCaller()

        // when
        underTest.logMessage(fakeMessage)

        // then
        assertThat(outputStream.toString())
            .isEqualTo(
                "V/RuntimeUtilsTest\$LogCal: $fakeMessage\n" +
                        "D/RuntimeUtilsTest\$LogCal: $fakeMessage\n" +
                        "I/RuntimeUtilsTest\$LogCal: $fakeMessage\n" +
                        "W/RuntimeUtilsTest\$LogCal: $fakeMessage\n" +
                        "E/RuntimeUtilsTest\$LogCal: $fakeMessage\n" +
                        "A/RuntimeUtilsTest\$LogCal: $fakeMessage\n"
            )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `devLogger will strip the anonymous part from caller name when resolving the tag`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {

        // given
        val fakeMessage = forge.anAlphabeticalString()
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.setFieldValue("isDebug", true)
        val underTest = object : Caller {
            override fun logMessage(message: String) {
                devLogger.v(message)
            }
        }

        // when
        underTest.logMessage(fakeMessage)

        // then
        assertThat(outputStream.toString())
            .isEqualTo(
                "V/RuntimeUtilsTest\$devLogger will strip " +
                        "the anonymous part from caller name when " +
                        "resolving the tag\$underTest: $fakeMessage\n"
            )
    }

    open class LogCaller : Caller {

        override fun logMessage(message: String) {
            devLogger.v(message)
            devLogger.d(message)
            devLogger.i(message)
            devLogger.w(message)
            devLogger.e(message)
            devLogger.wtf(message)
        }
    }

    interface Caller {
        fun logMessage(message: String)
    }
}
