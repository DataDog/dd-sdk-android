/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject

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

    fun hasSessionType(sessionType: String) {
        hasField("session.type", sessionType)
    }

    fun hasSource(source: String) {
        hasField("source", source)
    }

    fun hasType(type: String) {
        hasField("type", type)
    }

    fun hasViewUrl(viewUrl: String) {
        hasField("view.url", viewUrl)
    }

    fun hasViewName(viewName: String) {
        hasField("view.name", viewName)
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

    companion object {
        fun assertThat(actual: JsonObject): RumEventAssert {
            return RumEventAssert(actual)
        }
    }
}
