package com.datadog.tools.unit.extensions

import android.annotation.SuppressLint
import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
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
class SystemStreamExtension :
    ParameterResolver,
    BeforeEachCallback,
    BeforeAllCallback,
    AfterAllCallback {

    private lateinit var originalOutputStream: PrintStream
    private lateinit var originalErrorStream: PrintStream
    private lateinit var outputStream: ByteArrayOutputStream
    private lateinit var errorStream: ByteArrayOutputStream

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
        val outputStreamAnnotation = parameterContext.findAnnotation(SystemOutStream::class.java)
        val errorStreamAnnotation = parameterContext.findAnnotation(SystemErrorStream::class.java)

        return when {
            outputStreamAnnotation.isPresent -> outputStream
            errorStreamAnnotation.isPresent -> errorStream
            else -> ByteArrayOutputStream()
        }
    }

    // endregion

    // region BeforeAllCallback

    /** @inheritdoc */
    override fun beforeAll(context: ExtensionContext?) {
        originalOutputStream = System.out
        outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        originalErrorStream = System.err
        errorStream = ByteArrayOutputStream()
        System.setErr(PrintStream(errorStream))
    }

    // endregion

    // region BeforeEachExecutionCallback

    /** @inheritdoc */
    override fun beforeEach(context: ExtensionContext?) {
        outputStream.reset()
        errorStream.reset()
    }

    //endregion

    // region AfterAllCallback

    /** @inheritdoc */
    override fun afterAll(context: ExtensionContext?) {
        System.setErr(originalErrorStream)
        System.setOut(originalOutputStream)
    }

    // endregion
}
