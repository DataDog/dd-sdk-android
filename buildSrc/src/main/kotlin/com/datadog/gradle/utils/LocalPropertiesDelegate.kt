package com.datadog.gradle.utils

import org.gradle.api.Project
import org.jetbrains.kotlin.konan.properties.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LocalPropertiesDelegate : ReadOnlyProperty<Project, Properties> {
    private var localProperties: Properties? = null

    override fun getValue(thisRef: Project, property: KProperty<*>): Properties {
        if (localProperties == null) {
            localProperties = Properties().apply {
                val localPropsFile = thisRef.rootProject.file("local.properties")
                if (localPropsFile.exists()) {
                    load(localPropsFile.inputStream())
                }

            }
        }
        return localProperties!!
    }
}