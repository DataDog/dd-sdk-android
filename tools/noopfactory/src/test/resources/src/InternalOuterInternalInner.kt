@file:Suppress("MatchingDeclarationName")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

internal class InternalOuter {

    @NoOpImplementation
    internal interface InternalInner {
        fun doSomething(): Int
    }
}
