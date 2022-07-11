/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.connect
import com.datadog.android.rum.internal.domain.scope.dns
import com.datadog.android.rum.internal.domain.scope.download
import com.datadog.android.rum.internal.domain.scope.firstByte
import com.datadog.android.rum.internal.domain.scope.ssl
import com.datadog.android.rum.internal.domain.scope.toResourceSchemaType
import com.datadog.android.rum.model.ResourceEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

internal class ResourceEventForgeryFactory :
    ForgeryFactory<ResourceEvent> {
    override fun getForgery(forge: Forge): ResourceEvent {
        val timing = forge.aNullable<ResourceTiming>()
        return ResourceEvent(
            date = forge.aTimestamp(),
            resource = ResourceEvent.Resource(
                id = forge.aNullable { getForgery<UUID>().toString() },
                type = forge.getForgery(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                duration = forge.aPositiveLong(),
                method = forge.aNullable(),
                statusCode = forge.aNullable { aLong(200, 600) },
                size = forge.aNullable { aPositiveLong() },
                dns = timing?.dns(),
                connect = timing?.connect(),
                ssl = timing?.ssl(),
                firstByte = timing?.firstByte(),
                download = timing?.download(),
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
            action = forge.aNullable { ResourceEvent.Action(getForgery<UUID>().toString()) },
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
                val androidInfoProvider = getForgery(AndroidInfoProvider::class.java)
                ResourceEvent.Os(
                    name = androidInfoProvider.osName,
                    version = androidInfoProvider.osVersion,
                    versionMajor = androidInfoProvider.osMajorVersion
                )
            },
            device = forge.aNullable {
                val androidInfoProvider = getForgery(AndroidInfoProvider::class.java)
                ResourceEvent.Device(
                    name = androidInfoProvider.deviceName,
                    model = androidInfoProvider.deviceModel,
                    brand = androidInfoProvider.deviceBrand,
                    type = androidInfoProvider.deviceType.toResourceSchemaType()
                )
            },
            context = forge.aNullable {
                ResourceEvent.Context(
                    additionalProperties = forge.exhaustiveAttributes()
                )
            },
            dd = ResourceEvent.Dd(
                session = forge.aNullable { ResourceEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") },
                spanId = forge.aNullable { aNumericalString() },
                traceId = forge.aNullable { aNumericalString() }
            )
        )
    }
}
