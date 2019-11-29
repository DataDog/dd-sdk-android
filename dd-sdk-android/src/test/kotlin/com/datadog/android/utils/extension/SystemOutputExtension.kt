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
        val isMatchingType = (parameterContext
            ?.parameter?.type?.isAssignableFrom(ByteArrayOutputStream::class.java)
            ?: false)
        val isMatchingAnnotation =
            (parameterContext?.isAnnotated(SystemOutStream::class.java) ?: false) ||
                    (parameterContext?.isAnnotated(SystemErrorStream::class.java) ?: false)
        return isMatchingType && isMatchingAnnotation
    }

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?
    ): Any {
        val systemOutAnnotation = parameterContext?.findAnnotation(SystemOutStream::class.java)
        val errorOutAnnotation = parameterContext?.findAnnotation(SystemErrorStream::class.java)
        val byteStream = ByteArrayOutputStream()

        if (systemOutAnnotation?.isPresent == true) {
            System.setOut(PrintStream(byteStream))
        }

        if (errorOutAnnotation?.isPresent == true) {
            System.setErr(PrintStream(byteStream))
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
