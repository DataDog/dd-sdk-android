@file:Suppress("ktlint")

package com.example

import kotlin.OptIn
import kotlin.Suppress

internal class NoOpExperimentalInterface : ExperimentalInterface {
    @OptIn(ExperimentalApi::class)
    override fun doSomethingExperimental() {
    }
}
