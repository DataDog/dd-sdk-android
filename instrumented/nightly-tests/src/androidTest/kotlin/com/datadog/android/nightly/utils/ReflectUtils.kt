/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Invokes a method on the target instance.
 * @param methodName the name of the method
 * @param params the parameters to provide the method
 * @return the result from the invoked method
 */
@Suppress("SpreadOperator", "UNCHECKED_CAST", "TooGenericExceptionCaught")
fun <T : Any> T.invokeMethod(
    methodName: String,
    vararg params: Any?
): Any? {
    val declarationParams = Array<Class<*>?>(params.size) {
        params[it]?.javaClass
    }

    val method = getDeclaredMethodRecursively(methodName, true, declarationParams)
    val wasAccessible = method.isAccessible

    val output: Any?
    method.isAccessible = true
    try {
        output = if (params.isEmpty()) {
            method.invoke(this)
        } else {
            method.invoke(this, *params)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } finally {
        method.isAccessible = wasAccessible
    }

    return output
}

/**
 * Invokes a method on the target instance, where one or more of the parameters
 * are generics.
 * @param methodName the name of the method
 * @param params the parameters to provide the method
 * @return the result from the invoked method
 */
@Suppress("SpreadOperator", "UNCHECKED_CAST")
fun <T : Any> T.invokeGenericMethod(
    methodName: String,
    vararg params: Any
): Any? {
    val declarationParams = Array<Class<*>?>(params.size) {
        params[it].javaClass
    }

    val method = getDeclaredMethodRecursively(methodName, false, declarationParams)
    val wasAccessible = method.isAccessible

    val output: Any?
    method.isAccessible = true
    try {
        output = if (params.isEmpty()) {
            method.invoke(this)
        } else {
            method.invoke(this, *params)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } finally {
        method.isAccessible = wasAccessible
    }

    return output
}

@Suppress("TooGenericExceptionCaught", "SwallowedException", "SpreadOperator", "ComplexMethod")
private fun <T : Any> T.getDeclaredMethodRecursively(
    methodName: String,
    matchingParams: Boolean,
    declarationParams: Array<Class<*>?>
): Method {
    val classesToSearch = mutableListOf<Class<*>>(this.javaClass)
    val classesSearched = mutableListOf<Class<*>>()
    var method: Method?
    do {
        val lookingInClass = classesToSearch.removeAt(0)
        classesSearched.add(lookingInClass)
        method = try {
            if (matchingParams) {
                lookingInClass.getDeclaredMethod(methodName, *declarationParams)
            } else {
                lookingInClass.declaredMethods.firstOrNull {
                    it.name == methodName &&
                        it.parameterTypes.size == declarationParams.size
                }
            }
        } catch (e: Throwable) {
            null
        }

        val superclass = lookingInClass.superclass
        if (superclass != null &&
            !classesToSearch.contains(superclass) &&
            !classesSearched.contains(superclass)
        ) {
            classesToSearch.add(superclass)
        }
        lookingInClass.interfaces.forEach {
            if (!classesToSearch.contains(it) && !classesSearched.contains(it)) {
                classesToSearch.add(it)
            }
        }
    } while (method == null && classesToSearch.isNotEmpty())

    checkNotNull(method) {
        "Unable to access method $methodName on ${javaClass.canonicalName}"
    }

    return method
}

/**
 * Helper method to access security config while it's still private.
 * @param securityConfig the [SecurityConfig] to use
 */
fun Configuration.Builder.setSecurityConfig(
    securityConfig: SecurityConfig
): Configuration.Builder {
    val method = this.javaClass.declaredMethods.find { it.name == "setSecurityConfig" }
    method?.isAccessible = true
    return method?.invoke(this, securityConfig) as? Configuration.Builder ?: this
}
