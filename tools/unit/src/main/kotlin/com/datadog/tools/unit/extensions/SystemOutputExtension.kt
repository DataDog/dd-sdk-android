package com.datadog.tools.unit.extensions

import android.annotation.SuppressLint
import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * A JUnit5 [Extension] that will capture data printed on the [System.err] and [System.out] streams.
 *
 * You can then read the content printed by adding a [ByteArrayOutputStream] parameter to your
 * test method, with the @[SystemErrorStream] or @[SystemOutStream] annotation.
 */
@SuppressLint("NewApi")
class SystemOutputExtension :
    ParameterResolver,
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback {

    private lateinit var originalErrorStream: PrintStream
    private lateinit var originalOutputStream: PrintStream

    // region ParameterResolver

    /** @inheritdoc */
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {

        val isMatchingType = parameterContext.parameter.type == ByteArrayOutputStream::class.java
        val isMatchingAnnotation =
            parameterContext.isAnnotated(SystemOutStream::class.java) ||
                parameterContext.isAnnotated(SystemErrorStream::class.java)
        return isMatchingType && isMatchingAnnotation
    }

    /** @inheritdoc */
    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        val systemOutAnnotation = parameterContext.findAnnotation(SystemOutStream::class.java)
        val errorOutAnnotation = parameterContext.findAnnotation(SystemErrorStream::class.java)
        val byteStream = ByteArrayOutputStream()

        if (systemOutAnnotation?.isPresent == true) {
            System.setOut(PrintStream(byteStream))
        }

        if (errorOutAnnotation?.isPresent == true) {
            System.setErr(PrintStream(byteStream))
        }
        return byteStream
    }

    // endregion

    // region BeforeTestExecutionCallback

    /** @inheritdoc */
    override fun beforeTestExecution(context: ExtensionContext?) {
        originalErrorStream = System.err
        originalOutputStream = System.out
    }

    // endregion

    // region AfterTestExecutionCallback

    /** @inheritdoc */
    override fun afterTestExecution(context: ExtensionContext?) {
        System.setErr(originalErrorStream)
        System.setOut(originalOutputStream)
    }

    // endregion
}
