/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

class ErrorEventForgeryFactory : ForgeryFactory<ErrorEvent> {

    override fun getForgery(forge: Forge): ErrorEvent {
        return ErrorEvent(
            buildId = forge.aNullable { getForgery<UUID>().toString() },
            date = forge.aTimestamp(),
            error = ErrorEvent.Error(
                id = forge.aNullable { getForgery<UUID>().toString() },
                message = forge.anAlphabeticalString(),
                source = forge.getForgery(),
                stack = forge.aNullable { aThrowable().loggableStackTrace() },
                resource = forge.aNullable {
                    ErrorEvent.Resource(
                        url = aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                        method = getForgery(),
                        statusCode = aLong(200, 600),
                        provider = aNullable {
                            ErrorEvent.Provider(
                                domain = aNullable { aStringMatching("[a-z]+\\.[a-z]{3}") },
                                name = aNullable { anAlphabeticalString() },
                                type = aNullable()
                            )
                        }
                    )
                },
                sourceType = forge.aNullable { forge.getForgery() },
                isCrash = forge.aNullable { aBool() },
                type = forge.aNullable { anAlphabeticalString() },
                handling = forge.aNullable { getForgery() },
                handlingStack = forge.aNullable { aThrowable().loggableStackTrace() },
                category = forge.aNullable { getForgery() },
                threads = forge.aNullable {
                    aList {
                        ErrorEvent.Thread(
                            name = anAlphaNumericalString(),
                            crashed = aBool(),
                            stack = aThrowable().stackTraceToString(),
                            state = aNullable { getForgery<Thread.State>().name.lowercase() }
                        )
                    }
                },
                timeSinceAppStart = forge.aNullable { aPositiveLong() }
            ),
            view = ErrorEvent.ErrorEventView(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                name = forge.aNullable { anAlphabeticalString() },
                inForeground = forge.aNullable { aBool() }
            ),
            connectivity = forge.aNullable {
                ErrorEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aList { getForgery() },
                    cellular = aNullable {
                        ErrorEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            synthetics = forge.aNullable {
                ErrorEvent.Synthetics(
                    testId = forge.anHexadecimalString(),
                    resultId = forge.anHexadecimalString()
                )
            },
            usr = forge.aNullable {
                ErrorEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    anonymousId = aNullable { anHexadecimalString() },
                    additionalProperties = exhaustiveAttributes(excludedKeys = setOf("id", "name", "email"))
                )
            },
            action = forge.aNullable { ErrorEvent.Action(aList { getForgery<UUID>().toString() }) },
            application = ErrorEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = ErrorEvent.ErrorEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = ErrorEvent.ErrorEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(ErrorEvent.ErrorEventSource::class.java) },
            ciTest = forge.aNullable {
                ErrorEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                ErrorEvent.Os(
                    name = forge.aString(),
                    version = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}",
                    versionMajor = forge.aSmallInt().toString()
                )
            },
            device = forge.aNullable {
                ErrorEvent.Device(
                    name = forge.aString(),
                    model = forge.aString(),
                    brand = forge.aString(),
                    type = forge.aValueFrom(ErrorEvent.DeviceType::class.java),
                    architecture = forge.aString()
                )
            },
            context = forge.aNullable {
                ErrorEvent.Context(additionalProperties = forge.exhaustiveAttributes())
            },
            dd = ErrorEvent.Dd(
                session = forge.aNullable { ErrorEvent.DdSession(aNullable { getForgery() }) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            ),
            ddtags = forge.aNullable { ddTagsString() }
        )
    }
}
