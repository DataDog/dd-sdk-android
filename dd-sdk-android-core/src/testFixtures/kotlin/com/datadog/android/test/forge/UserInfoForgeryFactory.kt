/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.test.forge

import com.datadog.android.api.context.UserInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class UserInfoForgeryFactory : ForgeryFactory<UserInfo> {

    override fun getForgery(forge: Forge): UserInfo {
        return UserInfo(
            id = forge.aNullable { anHexadecimalString() },
            name = forge.aNullable { forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
            email = forge.aNullable { forge.aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
            additionalProperties = forge.exhaustiveAttributes()
        )
    }
}
