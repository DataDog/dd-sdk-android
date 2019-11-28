package com.datadog.android.utils.extension

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class SystemOutputExtension : ParameterResolver, BeforeTestExecutionCallback,
    AfterTestExecutionCallback {

    lateinit var originalErrorStream: PrintStream
    lateinit var originalOutputStream: PrintStream

    override fun supportsParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?
    ): Boolean {
        return parameterContext
            ?.parameter?.type?.isAssignableFrom(ByteArrayOutputStream::class.java)
            ?: false
    }

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?
    ): Any {
        val annotation = parameterContext?.parameter?.annotations?.get(0)?.annotationClass
        val byteStream = ByteArrayOutputStream()
        when (annotation) {
            SystemOutStream::class -> {
                System.setOut(PrintStream(byteStream))
            }
            SystemErrorStream::class -> {
                System.setErr(PrintStream(byteStream))
            }
            else -> {
                // do nothing
            }
        }
        return byteStream
    }

    override fun beforeTestExecution(context: ExtensionContext?) {
        originalErrorStream = System.err
        originalOutputStream = System.out
    }

    override fun afterTestExecution(context: ExtensionContext?) {
        System.setErr(originalErrorStream)
        System.setOut(originalOutputStream)
    }
}
