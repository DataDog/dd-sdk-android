/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.util.LinkedList

/**
 * Sets a static value on the target class.
 * @param T the type of the static field
 * @param R the type of the instance owning the static field
 * @param fieldName the name of the static field
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
 * @param T the type of the static field
 * @param R the type of the instance owning the static field
 * @param fieldName the name of the static field
 */
inline fun <reified T, reified R> Class<T>.getStaticValue(fieldName: String): R {
    val field = getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    return field.get(null) as R
}

/**
 * Sets the field value on the target instance.
 * @param T the type of the field
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
 * @param T the type of the field
 * @param R the type of the instance owning the field
 * @param fieldName the name of the field
 * @param enclosingClass the class on which the field is declared
 */
inline fun <reified T, R : Any> R.getFieldValue(
    fieldName: String,
    enclosingClass: Class<R> = this.javaClass
): T {
    val field = enclosingClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}

@SuppressWarnings("PrintStackTrace")
private fun <R> setFieldValue(instance: Any?, field: Field, fieldValue: R): Boolean {
    field.isAccessible = true
    // Make it non final
    try {
        // Android JVM does not use the JDK sources for reflection therefore the property access type
        // field is named `accessFlags` instead of `modifiers` as in a default JVM
        // Because these methods are being shared between JUnit and AndroidJUnit runtimes we will
        // have to support both implementations.
        RemoveFinalModifier.remove(field)
    } catch (e: NoSuchFieldException) {
        e.printStackTrace()
        return false
    }
    field.set(instance, fieldValue)
    return true
}

/**
 * Invokes a private constructor for the specified parameters.
 * @param T the type of the class
 * @param clazz the class to create an instance of
 * @param params the parameters to provide the constructor
 * @return the instance of the class
 */
@Suppress("SpreadOperator", "UNCHECKED_CAST")
fun <T : Any> createInstance(clazz: Class<T>, vararg params: Any?): T {
    val toTypedArray = params.map { it?.javaClass ?: Any::class.java }.toTypedArray()
    val constructor = clazz.declaredConstructors
        .filter { it.parameterTypes.size == toTypedArray.size }
        .first { constructor ->
            constructor.parameterTypes
                .mapIndexed { idx, clazz ->
                    clazz.isAssignableFrom(toTypedArray[idx])
                }.all { it }
        }
    constructor.isAccessible = true
    return constructor.newInstance(*params) as T
}

/**
 * Invokes a private constructor for the specified parameters without type check.
 * This is mostly useful when you provide Mock parameters where the type check fails for the constructor.
 * @param T the type of the class
 * @param clazz the class to create an instance of
 * @param params the parameters to provide the constructor
 * @return the instance of the class
 */
@Suppress("SpreadOperator", "UNCHECKED_CAST")
fun <T : Any> createInstanceWithoutTypeCheck(clazz: Class<T>, vararg params: Any?): T {
    val constructor = clazz.declaredConstructors.first { it.parameterTypes.size == params.size }
    constructor.isAccessible = true
    return constructor.newInstance(*params) as T
}

/**
 * Invokes a static method on the specified class.
 * @param T the type of the class
 * @param R the type of the result
 * @param methodName the name of the method
 * @param params the parameters to provide the method
 * @return the result of the method
 */
@Suppress("SpreadOperator")
inline fun <reified T : Any, reified R> Class<T>.callStaticMethod(
    methodName: String,
    vararg params: Any?
): R {
    val declarationParams = Array<Class<*>?>(params.size) {
        params[it]?.javaClass ?: Any::class.java
    }

    val method = this.getDeclaredMethod(methodName, *declarationParams)
    val wasAccessible = method.isAccessible

    val output: Any?
    method.isAccessible = true
    try {
        output = if (params.isEmpty()) {
            method.invoke(null)
        } else {
            method.invoke(null, *params)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } finally {
        method.isAccessible = wasAccessible
    }

    return output as R
}
