/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.RumResourceKind
import java.util.UUID

internal sealed class RumEventData(val category: String) {

    internal data class Resource(
        val kind: RumResourceKind,
        val url: String,
        val durationNanoSeconds: Long
    ) : RumEventData("resource")

    internal data class UserAction(
        val name: String,
        val id: UUID,
        val durationNanoSeconds: Long
    ) : RumEventData("user_action")

    internal data class View(
        val name: String,
        val durationNanoSeconds: Long,
        val measures: Measures = Measures(),
        val version: Int = 1
    ) : RumEventData("view") {

        fun incrementErrorCount(): View {
            return copy(
                measures = measures.incrementErrorCount()
            )
        }

        fun incrementResourceCount(): View {
            return copy(
                measures = measures.incrementResourceCount()
            )
        }

        fun incrementUserActionCount(): View {
            return copy(
                measures = measures.incrementUserActionCount()
            )
        }

        internal data class Measures(
            val errorCount: Int = 0,
            val resourceCount: Int = 0,
            val userActionCount: Int = 0
        ) {
            fun incrementErrorCount(): Measures {
                return copy(errorCount = errorCount + 1)
            }

            fun incrementResourceCount(): Measures {
                return copy(resourceCount = resourceCount + 1)
            }

            fun incrementUserActionCount(): Measures {
                return copy(userActionCount = userActionCount + 1)
            }
        }
    }

    internal data class Error(
        val message: String,
        val origin: String,
        val throwable: Throwable? = null
    ) : RumEventData("error")
}
