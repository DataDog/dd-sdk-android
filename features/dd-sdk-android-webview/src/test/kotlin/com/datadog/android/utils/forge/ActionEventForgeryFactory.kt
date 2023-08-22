/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.model.ActionEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class ActionEventForgeryFactory :
    ForgeryFactory<ActionEvent> {
    override fun getForgery(forge: Forge): ActionEvent {
        return ActionEvent(
            date = forge.aTimestamp(),
            action = ActionEvent.ActionEventAction(
                type = forge.getForgery(),
                id = forge.aNullable { getForgery<UUID>().toString() },
                target = forge.aNullable {
                    ActionEvent.ActionEventActionTarget(anAlphabeticalString())
                },
                error = forge.aNullable { ActionEvent.Error(aLong(0, 512)) },
                crash = forge.aNullable { ActionEvent.Crash(aLong(0, 512)) },
                resource = forge.aNullable { ActionEvent.Resource(aLong(0, 512)) },
                longTask = forge.aNullable { ActionEvent.LongTask(aLong(0, 512)) },
                loadingTime = forge.aNullable { aPositiveLong(strict = true) },
                frustration = forge.aNullable {
                    ActionEvent.Frustration(
                        type = forge.aList {
                            forge.aValueFrom(ActionEvent.Type::class.java)
                        }.distinct()
                    )
                }
            ),
            view = ActionEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                name = forge.aNullable { anAlphabeticalString() },
                inForeground = forge.aNullable { aBool() }
            ),
            connectivity = forge.aNullable {
                ActionEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aList { getForgery() },
                    cellular = aNullable {
                        ActionEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            synthetics = forge.aNullable {
                ActionEvent.Synthetics(
                    testId = forge.anHexadecimalString(),
                    resultId = forge.anHexadecimalString()
                )
            },
            usr = forge.aNullable {
                ActionEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    additionalProperties = exhaustiveAttributes()
                )
            },
            application = ActionEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = ActionEvent.ActionEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = ActionEvent.ActionEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(ActionEvent.Source::class.java) },
            ciTest = forge.aNullable {
                ActionEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                ActionEvent.Os(
                    name = anAlphaNumericalString(),
                    version = anAlphaNumericalString(),
                    versionMajor = anAlphaNumericalString()
                )
            },
            device = forge.aNullable {
                ActionEvent.Device(
                    name = anAlphaNumericalString(),
                    model = anAlphaNumericalString(),
                    brand = anAlphaNumericalString(),
                    type = aValueFrom(ActionEvent.DeviceType::class.java),
                    architecture = anAlphaNumericalString()
                )
            },
            context = forge.aNullable {
                ActionEvent.Context(additionalProperties = forge.exhaustiveAttributes())
            },
            dd = ActionEvent.Dd(
                session = forge.aNullable { ActionEvent.DdSession(aNullable { getForgery() }) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") }
            )
        )
    }
}
