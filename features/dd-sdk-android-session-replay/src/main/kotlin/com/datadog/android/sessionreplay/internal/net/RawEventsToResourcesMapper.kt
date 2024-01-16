/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import java.io.IOException
import java.io.ObjectInput

internal class RawEventsToResourcesMapper(private val internalLogger: InternalLogger) {

    fun map(
        objectInput: ObjectInput
    ): SessionReplayResource? {
        return deserializeSessionReplayResource(objectInput)
    }

    // region internal
    private fun deserializeSessionReplayResource(
        objectInput: ObjectInput
    ): SessionReplayResource? {
        return try {
            // exceptions are caught
            @Suppress("UnsafeThirdPartyFunctionCall")
            objectInput.readObject() as? SessionReplayResource
        } catch (e: ClassNotFoundException) {
            // should never happen
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { UNABLE_TO_DESERIALIZE_RESOURCE_ERROR },
                throwable = e
            )
            null
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { UNABLE_TO_DESERIALIZE_RESOURCE_ERROR },
                throwable = e
            )
            null
        } catch (e: ClassCastException) {
            // should never happen
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { UNABLE_TO_DESERIALIZE_RESOURCE_ERROR },
                throwable = e
            )
            null
        }
    }

    // endregion

    internal companion object {
        @VisibleForTesting
        internal const val UNABLE_TO_DESERIALIZE_RESOURCE_ERROR = "Unable to deserialize resource"
    }
}
