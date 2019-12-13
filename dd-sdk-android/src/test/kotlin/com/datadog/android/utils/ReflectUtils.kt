package com.datadog.android.utils

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal inline fun <reified T, R> Class<T>.setStaticValue(
    fieldName: String,
    fieldValue: R
) {

    val field = getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    // Make it non final
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
    field.set(null, fieldValue)
}

internal inline fun <reified T, reified R> Class<T>.getStaticValue(fieldName: String): R {

    val field = getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    return field.get(null) as R
}

internal inline fun <reified T> Any.getFieldValue(fieldName: String): T {
    val field = this.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}

internal fun Any.accessMethod(methodName: String, vararg params: Any) {
    val declarationParams = Array<Class<*>>(params.size) {
        params[it].javaClass
    }
    val method = this.javaClass.getDeclaredMethod(methodName, *declarationParams)
    method.isAccessible = true
    if (params.isEmpty()) {
        method.invoke(this)
    } else {
        method.invoke(this, params)
    }
}
