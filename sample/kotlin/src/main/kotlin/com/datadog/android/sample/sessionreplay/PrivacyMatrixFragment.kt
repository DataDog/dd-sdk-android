/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.setSessionReplayImagePrivacy
import com.datadog.android.sessionreplay.setSessionReplayTextAndInputPrivacy

/**
 * Exercises every `TextAndInputPrivacy` × `ImagePrivacy` masking permutation this pixel-capture
 * pipeline handles, all on one screen, via the public per-view privacy override API
 * (`View.setSessionReplayTextAndInputPrivacy`/`setSessionReplayImagePrivacy`) rather than the
 * app-wide [com.datadog.android.sessionreplay.SessionReplayConfiguration] — each row below applies
 * a *different* override to its own [PrivacyMatrixTextView]/[PrivacyMatrixImageView] instance,
 * independent of this app's actual global configuration.
 *
 * The expected outcome for each row is written directly above it in
 * `res/layout/fragment_privacy_matrix.xml` — check a real Session Replay recording of this screen
 * against those expectations to confirm the whole pipeline (OCR, [InputFieldDetector],
 * [BlinkingCursorTracker], [com.datadog.android.sessionreplay.internal.recorder.ImageContentDetector])
 * behaves as intended end to end, not just in isolation.
 *
 * Navigation: Home → Session Replay → Privacy Matrix
 */
internal class PrivacyMatrixFragment : Fragment(R.layout.fragment_privacy_matrix) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Only textAndInputPrivacy is overridden on these — imagePrivacy stays at the app-wide
        // default (MASK_NONE) so PixelCaptureEligibility's imagePrivacy gate never interferes here.
        applyTextPrivacy(
            view,
            R.id.matrix_text_sensitive_plain,
            R.id.matrix_text_sensitive_input,
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
        applyTextPrivacy(
            view,
            R.id.matrix_text_all_inputs_plain,
            R.id.matrix_text_all_inputs_input,
            TextAndInputPrivacy.MASK_ALL_INPUTS
        )
        applyTextPrivacy(view, R.id.matrix_text_all_plain, R.id.matrix_text_all_input, TextAndInputPrivacy.MASK_ALL)

        // Only imagePrivacy is overridden on these — textAndInputPrivacy stays at the app-wide
        // default (MASK_SENSITIVE_INPUTS) so its own gate never interferes here either.
        applyImagePrivacy(view, R.id.matrix_image_none_only, R.id.matrix_image_none_with_text, ImagePrivacy.MASK_NONE)
        applyImagePrivacy(
            view,
            R.id.matrix_image_large_only_only,
            R.id.matrix_image_large_only_with_text,
            ImagePrivacy.MASK_LARGE_ONLY
        )
        applyImagePrivacy(view, R.id.matrix_image_all_only, R.id.matrix_image_all_with_text, ImagePrivacy.MASK_ALL)
    }

    private fun applyTextPrivacy(root: View, plainId: Int, inputStyledId: Int, privacy: TextAndInputPrivacy) {
        root.findViewById<PrivacyMatrixTextView>(plainId).apply {
            styledAsInputField = false
            setSessionReplayTextAndInputPrivacy(privacy)
        }
        root.findViewById<PrivacyMatrixTextView>(inputStyledId).apply {
            styledAsInputField = true
            setSessionReplayTextAndInputPrivacy(privacy)
        }
    }

    private fun applyImagePrivacy(root: View, imageOnlyId: Int, imageWithTextId: Int, privacy: ImagePrivacy) {
        root.findViewById<PrivacyMatrixImageView>(imageOnlyId).apply {
            includeText = false
            setSessionReplayImagePrivacy(privacy)
        }
        root.findViewById<PrivacyMatrixImageView>(imageWithTextId).apply {
            includeText = true
            setSessionReplayImagePrivacy(privacy)
        }
    }
}
