/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy

/**
 * Allows setting a view to be "hidden" in the hierarchy in Session Replay.
 *
 * @param hide pass `true` to hide the composable, or `false` to remove the override
 */
fun Modifier.sessionReplayHide(hide: Boolean): Modifier {
    return this.semantics {
        this.hide = hide
    }
}

/**
 * Allows overriding the image privacy for a view in Session Replay.
 *
 * @param imagePrivacy the new privacy level to use for the composable.
 */
fun Modifier.sessionReplayImagePrivacy(imagePrivacy: ImagePrivacy): Modifier {
    return this.semantics {
        this.imagePrivacy = imagePrivacy
    }
}

/**
 * Allows overriding the text and input privacy for a view in Session Replay.
 *
 * @param textAndInputPrivacy the new privacy level to use for the composable.
 */
fun Modifier.sessionReplayTextAndInputPrivacy(textAndInputPrivacy: TextAndInputPrivacy): Modifier {
    return this.semantics {
        this.textInputPrivacy = textAndInputPrivacy
    }
}

/**
 * Allows overriding the touch privacy for a view in Session Replay.
 *
 * @param touchPrivacy the new privacy level to use for the composable
 * or null to remove the override.
 */
fun Modifier.sessionReplayTouchPrivacy(touchPrivacy: TouchPrivacy): Modifier {
    return this.semantics {
        this.touchPrivacy = touchPrivacy
    }
}

internal val SessionReplayHidePropertyKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey(
    name = "_dd_session_replay_hide"
)

internal val ImagePrivacySemanticsPropertyKey: SemanticsPropertyKey<ImagePrivacy> =
    SemanticsPropertyKey(
        name = "_dd_session_replay_image_privacy"
    )

internal val TextInputSemanticsPropertyKey: SemanticsPropertyKey<TextAndInputPrivacy> =
    SemanticsPropertyKey(
        name = "_dd_session_replay_text_input_privacy"
    )

internal val TouchSemanticsPropertyKey: SemanticsPropertyKey<TouchPrivacy> = SemanticsPropertyKey(
    name = "_dd_session_replay_touch_privacy"
)

private var SemanticsPropertyReceiver.hide by SessionReplayHidePropertyKey
private var SemanticsPropertyReceiver.imagePrivacy by ImagePrivacySemanticsPropertyKey
private var SemanticsPropertyReceiver.textInputPrivacy by TextInputSemanticsPropertyKey
private var SemanticsPropertyReceiver.touchPrivacy by TouchSemanticsPropertyKey
