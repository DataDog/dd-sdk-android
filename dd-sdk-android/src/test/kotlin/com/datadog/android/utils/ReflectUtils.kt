package com.datadog.android.utils

import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal inline fun <reified T, R> Class<T>.setStaticValue(
    fieldName: String,
    fieldValue: R
) {

    val field = getDeclaredField(fieldName)
    field
    // make it accessible

    field.isAccessible = true

    // Make it non final
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
    field.set(null, fieldValue)
}
