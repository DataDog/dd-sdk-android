package com.example

import kotlin.Suppress
import kotlin.Unit

@Suppress("RedundantUnitReturnType")
internal class NoOpInheritedInterface : InheritedInterface {

    public override fun doSomething(): Unit {
    }

    public override fun parentMethod(): Unit {
    }

    public override fun rootMethod(): Unit {
    }
}
