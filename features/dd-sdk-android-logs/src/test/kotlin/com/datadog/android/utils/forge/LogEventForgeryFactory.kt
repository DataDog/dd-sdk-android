/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.log.model.LogEvent
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class LogEventForgeryFactory : ForgeryFactory<LogEvent> {
    override fun getForgery(forge: Forge): LogEvent {
        val networkInfo: NetworkInfo? = forge.aNullable()
        val userInfo: UserInfo? = forge.aNullable()
        val reservedKeysAsSet = mutableSetOf<String>().apply {
            LogEvent.RESERVED_PROPERTIES.forEach {
                this.add(it)
            }
        }

        return LogEvent(
            service = forge.anAlphabeticalString(),
            status = forge.aValueFrom(LogEvent.Status::class.java),
            message = forge.anAlphabeticalString(),
            date = forge.aFormattedTimestamp(),
            buildId = forge.aNullable { getForgery<UUID>().toString() },
            error = forge.aNullable {
                val throwable = forge.aNullable { aThrowable() }
                LogEvent.Error(
                    message = throwable?.message,
                    stack = throwable?.stackTraceToString(),
                    kind = throwable?.javaClass?.canonicalName ?: throwable?.javaClass?.simpleName
                )
            },
            additionalProperties = forge.exhaustiveAttributes(
                excludedKeys = reservedKeysAsSet,
                filterThreshold = 0f
            ),
            ddtags = forge.exhaustiveTags().joinToString(separator = ","),
            usr = forge.aNullable {
                LogEvent.Usr(
                    id = userInfo?.id,
                    name = userInfo?.name,
                    email = userInfo?.email,
                    additionalProperties = userInfo?.additionalProperties
                        ?.toMutableMap() ?: mutableMapOf()

                )
            },
            network = forge.aNullable {
                LogEvent.Network(
                    client = LogEvent.Client(
                        simCarrier = forge.aNullable {
                            LogEvent.SimCarrier(
                                id = networkInfo?.carrierId?.toString(),
                                name = networkInfo?.carrierName
                            )
                        },
                        signalStrength = networkInfo?.strength?.toString(),
                        uplinkKbps = networkInfo?.upKbps?.toString(),
                        downlinkKbps = networkInfo?.downKbps?.toString(),
                        connectivity = networkInfo?.connectivity?.toString().orEmpty()
                    )
                )
            },
            logger = LogEvent.Logger(
                name = forge.anAlphabeticalString(),
                version = forge.aStringMatching("[0-9]\\.[0-9]\\.[0-9]"),
                threadName = forge.aNullable { forge.anAlphabeticalString() }
            ),
            dd = LogEvent.Dd(
                device = LogEvent.Device(
                    architecture = forge.anAlphaNumericalString()
                )
            )
        )
    }

    private fun Forge.exhaustiveTags(): List<String> {
        return aList { aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?") }
    }
}
