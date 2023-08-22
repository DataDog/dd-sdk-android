/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import java.lang.reflect.Field
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
