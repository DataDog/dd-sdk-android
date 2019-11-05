package com.datadog.gradle.plugin

import java.io.File

open class ThirdPartyLicensesExtension(
    var output: File = File(DEFAULT_TP_LICENCE_FILENAME),
    var transitiveDependencies: Boolean = false
) {
    companion object {
        const val DEFAULT_TP_LICENCE_FILENAME = "LICENSE-3rdparty.csv"
    }
}
