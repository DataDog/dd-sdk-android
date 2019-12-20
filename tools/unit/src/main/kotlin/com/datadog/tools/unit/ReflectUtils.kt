package com.datadog.tools.unit

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Sets a static value on the target class.
 * @param fieldName the name of the field
 * @param fieldValue the value to set
 */
inline fun <reified T, R> Class<T>.setStaticValue(
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
 * Gets the field value from the target instance.
 * @param fieldName the name of the field
 */
inline fun <reified T> Any.getFieldValue(fieldName: String): T {
    val field = this.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}

/**
 * Invokes a method on the target instance.
 * @param methodName the name of the method
 * @param params the parameters to provide the methoc
 */
@Suppress("SpreadOperator")
fun Any.invokeMethod(methodName: String, vararg params: Any) {
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
