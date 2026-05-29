@file:Suppress("MatchingDeclarationName", "UnusedPrivateClass")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

private class PrivateOuter {

    @NoOpImplementation
    internal interface InternalInner {
        fun doSomething(): Int
    }
}
