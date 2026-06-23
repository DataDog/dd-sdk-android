@file:Suppress("MatchingDeclarationName")

package com.example

import com.datadog.tools.annotation.NoOpImplementation

internal abstract class InternalOuter {

    @NoOpImplementation
    protected interface ProtectedInner {
        fun doSomething(): Int
    }
}
