@file:Suppress("MatchingDeclarationName", "UnusedPrivateClass")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

private abstract class PrivateOuter {

    @NoOpImplementation
    protected interface ProtectedInner {
        fun doSomething(): Int
    }
}
