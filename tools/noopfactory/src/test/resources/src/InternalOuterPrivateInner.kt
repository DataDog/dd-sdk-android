@file:Suppress("MatchingDeclarationName", "UnusedPrivateClass")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

internal class InternalOuter {

    @NoOpImplementation
    private interface PrivateInner {
        fun doSomething(): Int
    }
}
