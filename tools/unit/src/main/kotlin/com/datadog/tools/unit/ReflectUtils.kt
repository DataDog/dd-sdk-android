package com.datadog.tools.unit

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
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
 * Sets the field value on the target instance.
 * @param fieldName the name of the field
 * @param fieldValue the value of the field
 */
inline fun <reified T> Any.setFieldValue(
    fieldName: String,
    fieldValue: T
) {
    val field = javaClass.getDeclaredField(fieldName)

    // make it accessible
    field.isAccessible = true

    // Make it non final
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

    field.set(this, fieldValue)
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
 * @param methodEnclosingClass the class where the method could be found.
 * By default is the current instance subclass.
 * @param params the parameters to provide the method
 */
@Suppress("SpreadOperator")
fun <T : Any> T.invokeMethod(
    methodName: String,
    methodEnclosingClass: Class<T> = this.javaClass,
    vararg params: Any
) {
    val declarationParams = Array<Class<*>>(params.size) {
        params[it].javaClass
    }
    val method = methodEnclosingClass.getDeclaredMethod(methodName, *declarationParams)
    method.isAccessible = true
    try {
        if (params.isEmpty()) {
            method.invoke(this)
        } else {
            method.invoke(this, *params)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
}
