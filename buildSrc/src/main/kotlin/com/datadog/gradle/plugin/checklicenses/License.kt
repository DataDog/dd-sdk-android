/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

sealed class License {

    data class SPDX(val licenses: List<SPDXLicense>) : License() {
        override fun toString(): String {
            return licenses.joinToString("/") { it.csvName }
        }
    }

    data class Raw(val value: String) : License() {
        override fun toString(): String {
            return "\"$value\""
        }
    }

    object Empty : License() {
        override fun toString(): String {
            return "__"
        }
    }

    companion object {
        fun from(license: String?): License {
            val licenseOrEmpty = license.orEmpty()
            val matches =
                SPDXLicenceConverter.convert(
                    licenseOrEmpty
                )
            return when {
                licenseOrEmpty.isEmpty() -> Empty
                matches.isNullOrEmpty() -> Raw(
                    licenseOrEmpty
                )
                else -> SPDX(
                    matches
                )
            }
        }
    }
}
