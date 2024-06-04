@file:Suppress("ktlint")

package com.example

import kotlin.String
import kotlin.Suppress

internal class NoOpInheritedInterface : InheritedInterface {
    override var mutableProperty: String = ""

    override val immutableProperty: String = ""

    override fun doSomething() {
    }

    override fun parentMethod() {
    }

    override fun rootMethod() {
    }
}
