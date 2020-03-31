/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

data class ThirdPartyDependency(
    val component: Component,
    val origin: String,
    val license: License,
    val copyright: String
) {
    enum class Component(val csvName: String) {
        IMPORT("import"),
        IMPORT_TEST("import(test)"),
        BUILD("build"),
        UNKNOWN("__")
    }

    override fun toString(): String {
        return "${component.csvName},$origin,$license,__"
    }
}
