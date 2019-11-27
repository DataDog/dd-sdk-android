package com.datadog.android.log

inline fun <reified T> Logger.getField(fieldName: String): T {
    val field = this.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}
