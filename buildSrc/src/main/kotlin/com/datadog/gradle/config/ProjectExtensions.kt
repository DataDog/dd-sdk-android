package com.datadog.gradle.config

import com.datadog.gradle.utils.LocalPropertiesDelegate
import org.gradle.api.Project
import org.jetbrains.kotlin.konan.properties.Properties

val Project.localProperties: Properties by LocalPropertiesDelegate()
