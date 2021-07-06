/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedList
import kotlin.reflect.jvm.isAccessible

/**
 * Creates an instance of the given class name.
 * @param className the full name of the class to instantiate
 * @param params the parameters to provide the constructor
 * @return the created instance
 */
@Suppress("SpreadOperator")
fun createInstance(
    className: String,
    vararg params: Any?
): Any {
    return Class.forName(className)
        .kotlin
        .constructors.first()
        .apply { isAccessible = true }
        .call(*params)
}

/**
 * Sets a static value on the target class.
 * @param fieldName the name of the field
 * @param fieldValue the value to set
 */
@Suppress("SwallowedException")
fun <T, R> Class<T>.setStaticValue(
    fieldName: String,
    fieldValue: R
) {
    val field = getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    setFieldValue(null, field, fieldValue)
}

/**
 * Gets the static value from the target class.
 * @param className the full name of the class
 * @param fieldName the name of the field
 */
inline fun <reified R> getStaticValue(
    className: String,
    fieldName: String
): R {
    val clazz = Class.forName(className)
    val field = clazz.getDeclaredField(fieldName)
    // make it accessible
    field.isAccessible = true

    return field.get(null) as R
}

/**
 * Gets the static value from the target class.
 * @param fieldName the name of the field
 */
inline fun <reified T, reified R> Class<T>.getStaticValue(fieldName: String): R {

    val field = getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    return field.get(null) as R
}

/**
 * Sets the field value on the target instance.
 * @param fieldName the name of the field
 * @param fieldValue the value of the field
 */
@Suppress("SwallowedException")
fun <T> Any.setFieldValue(
    fieldName: String,
    fieldValue: T
): Boolean {
    var field: Field? = null
    val classesToSearch = LinkedList<Class<*>>()
    classesToSearch.add(this.javaClass)
    val classesSearched = mutableSetOf<Class<*>>()

    while (field == null && classesToSearch.isNotEmpty()) {
        val toSearchIn = classesToSearch.remove()
        try {
            field = toSearchIn.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            // do nothing
        }
        classesSearched.add(toSearchIn)
        toSearchIn.superclass?.let {
            if (!classesSearched.contains(it)) {
                classesToSearch.add(it)
            }
        }
    }
    return if (field != null) {
        setFieldValue(this, field, fieldValue)
    } else {
        false
    }
}

/**
 * Gets the field value from the target instance.
 * @param fieldName the name of the field
 */
inline fun <reified T, R : Any> R.getFieldValue(
    fieldName: String,
    enclosingClass: Class<R> = this.javaClass
): T {
    val field = enclosingClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}

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

@Suppress("TooGenericExceptionCaught", "SwallowedException", "SpreadOperator")
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

@SuppressWarnings("PrintStackTrace")
private fun <R> setFieldValue(instance: Any?, field: Field, fieldValue: R): Boolean {
    field.isAccessible = true
    // Make it non final
    try {
        val accessField = resolveAccessField()
        accessField.isAccessible = true
        accessField.setInt(field, field.modifiers and Modifier.FINAL.inv())
    } catch (e: NoSuchFieldException) {
        e.printStackTrace()
        return false
    }
    field.set(instance, fieldValue)
    return true
}

@SuppressWarnings("SwallowedException")
private fun resolveAccessField(): Field {
    // Android JVM does not use the JDK sources for reflection therefore the property access type
    // field is named `accessFlags` instead of `modifiers` as in a default JVM
    // Because these methods are being shared between JUnit and AndroidJUnit runtimes we will
    // have to support both implementations.
    return try {
        Field::class.java.getDeclaredField("modifiers")
    } catch (e: NoSuchFieldException) {
        Field::class.java.getDeclaredField("accessFlags")
    }
}
