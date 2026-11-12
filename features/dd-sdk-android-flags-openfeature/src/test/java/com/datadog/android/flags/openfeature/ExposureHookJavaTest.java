/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.datadog.android.api.InternalLogger;
import com.datadog.android.flags.openfeature.internal.exposure.DatadogProviderMetadata;
import com.datadog.android.flags.openfeature.internal.exposure.ExposureDecisions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.openfeature.kotlin.sdk.EvaluationMetadata;
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails;
import dev.openfeature.kotlin.sdk.FlagValueType;
import dev.openfeature.kotlin.sdk.HookContext;

/**
 * Java callbacks see {@code java.util.Map} with its mutators, so the features map must accept them.
 */
class ExposureHookJavaTest {

    private static final String FLAG_KEY = "holdout-flag";

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void mutatingFeaturesFromJavaKeepsTheNextCallbackFeatures(int featureCount) {
        // Given
        InternalLogger logger = mock(InternalLogger.class);
        ExposureDecisions decisions = new ExposureDecisions();
        DatadogProviderMetadata providerMetadata = new DatadogProviderMetadata("Datadog", decisions, logger);
        EvaluationMetadata metadata = EvaluationMetadata.Companion.builder()
            .putString("allocationKey", "allocation")
            .putInt("__dd_exposure_decision", 1)
            .build();
        Map<String, Object> features = new LinkedHashMap<>();
        if (featureCount > 0) features.put("holdout.key", "holdout-1");
        if (featureCount > 1) features.put("holdout.assignment_group", "status_quo");
        decisions.register(metadata, features, null, () -> false);
        FlagEvaluationDetails<Object> details =
            new FlagEvaluationDetails<>(FLAG_KEY, true, "on", null, null, null, metadata);
        HookContext<Object> context =
            new HookContext<>(FLAG_KEY, FlagValueType.BOOLEAN, false, null, null, providerMetadata);
        List<String> completedSteps = new ArrayList<>();
        ExposureHook changingHook = new ExposureHook(evaluation -> {
            Map<String, Object> received = evaluation.getFeatures();
            received.put("added", true);
            completedSteps.add("put");
            received.remove("holdout.key");
            completedSteps.add("remove");
            received.clear();
            completedSteps.add("clear");
        });
        List<Map<String, Object>> laterFeatures = new ArrayList<>();
        ExposureHook readingHook = new ExposureHook(evaluation -> laterFeatures.add(evaluation.getFeatures()));

        // When
        changingHook.finallyAfter(context, details, Collections.emptyMap());
        readingHook.finallyAfter(context, details, Collections.emptyMap());

        // Then
        assertThat(completedSteps).containsExactly("put", "remove", "clear");
        assertThat(laterFeatures).hasSize(1);
        assertThat(laterFeatures.get(0)).isEqualTo(features);
        verifyNoInteractions(logger);
    }
}
