/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.model.ResourceEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class ResourceEventForgeryFactory :
    ForgeryFactory<ResourceEvent> {
    override fun getForgery(forge: Forge): ResourceEvent {
        return ResourceEvent(
            date = forge.aTimestamp(),
            resource = ResourceEvent.Resource(
                id = forge.aNullable { getForgery<UUID>().toString() },
                type = forge.getForgery(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                duration = forge.aNullable { aPositiveLong() },
                method = forge.aNullable(),
                statusCode = forge.aNullable { aLong(200, 600) },
                size = forge.aNullable { aPositiveLong() },
                dns = forge.aNullable {
                    ResourceEvent.Dns(
                        start = forge.aPositiveLong(),
                        duration = forge.aPositiveLong()
                    )
                },
                connect = forge.aNullable {
                    ResourceEvent.Connect(
                        start = forge.aPositiveLong(),
                        duration = forge.aPositiveLong()
                    )
                },
                ssl = forge.aNullable {
                    ResourceEvent.Ssl(
                        start = forge.aPositiveLong(),
                        duration = forge.aPositiveLong()
                    )
                },
                firstByte = forge.aNullable {
                    ResourceEvent.FirstByte(
                        start = forge.aPositiveLong(),
                        duration = forge.aPositiveLong()
                    )
                },
                download = forge.aNullable {
                    ResourceEvent.Download(
                        start = forge.aPositiveLong(),
                        duration = forge.aPositiveLong()
                    )
                },
                redirect = forge.aNullable {
                    ResourceEvent.Redirect(
                        aPositiveLong(),
                        aPositiveLong()
                    )
                },
                provider = forge.aNullable {
                    ResourceEvent.Provider(
                        domain = aNullable { aStringMatching("[a-z]+\\.[a-z]{3}") },
                        name = aNullable { anAlphabeticalString() },
                        type = aNullable()
                    )
                }
            ),
            view = ResourceEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                name = forge.aNullable { anAlphabeticalString() }
            ),
            connectivity = forge.aNullable {
                ResourceEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aList { getForgery() },
                    cellular = aNullable {
                        ResourceEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            synthetics = forge.aNullable {
                ResourceEvent.Synthetics(
                    testId = forge.anHexadecimalString(),
                    resultId = forge.anHexadecimalString()
                )
            },
            usr = forge.aNullable {
                ResourceEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    additionalProperties = exhaustiveAttributes()
                )
            },
            action = forge.aNullable {
                ResourceEvent.Action(aList { getForgery<UUID>().toString() })
            },
            application = ResourceEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = ResourceEvent.ResourceEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = ResourceEvent.ResourceEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(ResourceEvent.Source::class.java) },
            ciTest = forge.aNullable {
                ResourceEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                ResourceEvent.Os(
                    name = anAlphaNumericalString(),
                    version = anAlphaNumericalString(),
                    versionMajor = anAlphaNumericalString()
                )
            },
            device = forge.aNullable {
                ResourceEvent.Device(
                    name = anAlphaNumericalString(),
                    model = anAlphaNumericalString(),
                    brand = anAlphaNumericalString(),
                    type = aValueFrom(ResourceEvent.DeviceType::class.java),
                    architecture = anAlphaNumericalString()
                )
            },
            context = forge.aNullable {
                ResourceEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = ResourceEvent.Dd(
                session = forge.aNullable { ResourceEvent.DdSession(aNullable { getForgery() }) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") },
                spanId = forge.aNullable { aNumericalString() },
                traceId = forge.aNullable { aNumericalString() }
            )
        )
    }
}
