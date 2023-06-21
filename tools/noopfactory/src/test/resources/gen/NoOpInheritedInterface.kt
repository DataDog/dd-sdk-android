@file:Suppress("ktlint")

package com.example

import kotlin.String
import kotlin.Suppress
import kotlin.Unit

@Suppress("RedundantUnitReturnType")
internal class NoOpInheritedInterface : InheritedInterface {
    public override var mutableProperty: String = ""

    public override val immutableProperty: String = ""

    public override fun doSomething(): Unit {
    }

    public override fun parentMethod(): Unit {
    }

    public override fun rootMethod(): Unit {
    }
}
