/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import org.assertj.core.data.Offset

class RumEventAssert(actual: JsonObject) :
    JsonObjectAssert(actual, true) {

    // region Common Attributes

    fun hasService(service: String): RumEventAssert {
        hasField("service", service)
        return this
    }

    fun hasApplicationId(applicationId: String): RumEventAssert {
        hasField("application.id", applicationId)
        return this
    }

    fun hasSessionType(sessionType: String): RumEventAssert {
        hasField("session.type", sessionType)
        return this
    }

    fun hasSource(source: String): RumEventAssert {
        hasField("source", source)
        return this
    }

    fun hasType(type: String): RumEventAssert {
        hasField("type", type)
        return this
    }

    fun hasViewUrl(viewUrl: String): RumEventAssert {
        hasField("view.url", viewUrl)
        return this
    }

    fun hasViewName(viewName: String): RumEventAssert {
        hasField("view.name", viewName)
        return this
    }

    fun hasFeatureFlag(key: String, value: String): RumEventAssert {
        hasField("feature_flags.$key", value)
        return this
    }

    fun hasActionName(actionName: String): RumEventAssert {
        hasField("action.target.name", actionName)
        return this
    }

    // endregion

    // region View Attribute

    fun hasDocumentVersion(version: Int): RumEventAssert {
        // hack because our first RUM View event will always have document_version set to 2
        hasField("_dd.document_version", (version + 1))
        return this
    }

    fun hasViewIsActive(active: Boolean): RumEventAssert {
        hasField("view.is_active", active)
        return this
    }

    fun hasActionCount(count: Int): RumEventAssert {
        hasField("view.action.count", count)
        return this
    }

    fun hasErrorCount(count: Int): RumEventAssert {
        hasField("view.error.count", count)
        return this
    }

    fun hasResourceCount(count: Int): RumEventAssert {
        hasField("view.resource.count", count)
        return this
    }

    // endregion

    // region Resource Attributes

    fun hasResourceUrl(url: String): RumEventAssert {
        hasField("resource.url", url)
        return this
    }

    // endregion

    // region Error Attributes

    fun hasErrorType(kind: String): RumEventAssert {
        hasField("error.type", kind)
        return this
    }

    fun hasErrorMessage(message: String): RumEventAssert {
        hasField("error.message", message)
        return this
    }

    fun hasErrorFingerprint(fingerprint: String): RumEventAssert {
        hasField("error.fingerprint", fingerprint)
        return this
    }

    // endregion

    // region Action Attributes

    fun hasActionTargetName(name: String): RumEventAssert {
        hasField("action.target.name", name)
        return this
    }

    // endregion

    // region view loading time

    fun hasViewLoadingTime(time: Long, offset: Offset<Long>): RumEventAssert {
        hasField("view.loading_time", time, offset = offset)
        return this
    }

    fun doesNotHaveViewLoadingTime(): RumEventAssert {
        doesNotHaveField("view.loading_time")
        return this
    }

    // endregion

    companion object {
        fun assertThat(actual: JsonObject): RumEventAssert {
            return RumEventAssert(actual)
        }
    }
}
