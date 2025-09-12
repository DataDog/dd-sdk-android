package com.datadog.trace.common.sampling;

import androidx.annotation.NonNull;

import com.datadog.trace.api.sampling.SamplingRule;
import com.datadog.trace.core.util.JsonObjectUtils;
import com.datadog.trace.core.util.MapUtils;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents list of Trace Sampling Rules read from JSON. See TRACE_SAMPLING_RULES
 */
public class TraceSamplingRules {

    public static final TraceSamplingRules EMPTY = new TraceSamplingRules(Collections.emptyList());
    private static final Logger log = LoggerFactory.getLogger(TraceSamplingRules.class);
    private final List<Rule> rules;

    public TraceSamplingRules(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(rules);
    }

    public static TraceSamplingRules deserialize(String json) {
        TraceSamplingRules result = EMPTY;
        try {
            result = filterOutNullRules(deserializeRules(json));
        } catch (Throwable ex) {
            log.error("Couldn't parse Trace Sampling Rules from JSON: {}", json, ex);
        }
        return result;
    }

    private static TraceSamplingRules filterOutNullRules(List<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return EMPTY;
        }
        List<Rule> notNullRules = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            if (rule != null) {
                notNullRules.add(rule);
            }
        }
        if (notNullRules.isEmpty()) {
            return EMPTY;
        }
        return new TraceSamplingRules(notNullRules);
    }

    private static List<Rule> deserializeRules(String json) throws IllegalStateException {
        final JsonArray rulesAsJsonArray = JsonParser.parseString(json).getAsJsonArray();
        if (rulesAsJsonArray == null || rulesAsJsonArray.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Rule> rules = new LinkedList<>();
        for (int i = 0; i < rulesAsJsonArray.size(); i++) {
            final JsonRule rule = JsonRule.deserializeRule(rulesAsJsonArray.get(i).getAsJsonObject());
            rules.add(Rule.create(rule));
        }
        return rules;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public boolean isEmpty() {
        return this.rules.isEmpty();
    }

    public static final class Rule implements SamplingRule.TraceSamplingRule {
        private final String service;
        private final String name;
        private final String resource;
        private final Map<String, String> tags;
        private final double sampleRate;

        private Rule(
                String service, String name, String resource, Map<String, String> tags, double sampleRate) {
            this.service = service;
            this.name = name;
            this.resource = resource;
            this.tags = tags;
            this.sampleRate = sampleRate;
        }

        /**
         * Validate and create a {@link Rule} from its {@link JsonRule} representation.
         *
         * @param jsonRule The {@link JsonRule} to validate.
         * @return A {@link Rule} if the {@link JsonRule} is valid, {@code null} otherwise.
         */
        public static Rule create(JsonRule jsonRule) {
            String service = SamplingRule.normalizeGlob(jsonRule.service);
            String name = SamplingRule.normalizeGlob(jsonRule.name);
            String resource = SamplingRule.normalizeGlob(jsonRule.resource);
            Map<String, String> tags = jsonRule.tags;
            if (tags == null) {
                tags = Collections.emptyMap();
            }
            double sampleRate = 1D;
            if (jsonRule.sample_rate != null) {
                try {
                    sampleRate = Double.parseDouble(jsonRule.sample_rate);
                } catch (NumberFormatException ex) {
                    logRuleError(jsonRule, "sample_rate must be a number between 0.0 and 1.0");
                    return null;
                }
                if (sampleRate < 0D || sampleRate > 1D) {
                    logRuleError(jsonRule, "sample_rate must be between 0.0 and 1.0");
                    return null;
                }
            }
            return new Rule(service, name, resource, tags, sampleRate);
        }

        private static void logRuleError(JsonRule rule, String error) {
            log.error("Skipping invalid Trace Sampling Rule: {} - {}", rule, error);
        }

        @Override
        public String getService() {
            return service;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getResource() {
            return resource;
        }

        @Override
        public Map<String, String> getTags() {
            return tags;
        }

        @Override
        public double getSampleRate() {
            return sampleRate;
        }

        public String asStringJsonRule() {
            final JsonRule jsonRule = new JsonRule();
            jsonRule.service = service;
            jsonRule.name = name;
            jsonRule.resource = resource;
            jsonRule.tags = tags;
            jsonRule.sample_rate = String.valueOf(sampleRate);
            return jsonRule.toString();
        }
    }

    private static final class JsonRule {

        static final String SERVICE_KEY = "service";
        static final String NAME_KEY = "name";
        static final String RESOURCE_KEY = "resource";
        static final String TAGS_KEY = "tags";
        static final String TARGET_SPAN_KEY = "target_span";
        static final String SAMPLE_RATE_KEY = "sample_rate";

        private static JsonRule deserializeRule(JsonObject ruleAsJsonObject) {
            final JsonRule rule = new JsonRule();
            rule.name = JsonObjectUtils.getAsString(ruleAsJsonObject, NAME_KEY);
            rule.resource = JsonObjectUtils.getAsString(ruleAsJsonObject, RESOURCE_KEY);
            rule.sample_rate = JsonObjectUtils.getAsString(ruleAsJsonObject, SAMPLE_RATE_KEY);
            rule.service = JsonObjectUtils.getAsString(ruleAsJsonObject, SERVICE_KEY);
            rule.target_span = JsonObjectUtils.getAsString(ruleAsJsonObject, TARGET_SPAN_KEY);
            rule.tags = JsonObjectUtils.safeGetAsMap(ruleAsJsonObject, TAGS_KEY);
            return rule;
        }

        String service;
        String name;
        String resource;
        Map<String, String> tags;
        String target_span;
        String sample_rate;

        @NonNull
        @Override
        public String toString() {
            final JsonObject jsonObject = new JsonObject();
            if (name != null) {
                jsonObject.addProperty(NAME_KEY, name);
            }
            if (resource != null) {
                jsonObject.addProperty(RESOURCE_KEY, resource);
            }
            if (sample_rate != null) {
                jsonObject.addProperty(SAMPLE_RATE_KEY, sample_rate);
            }
            if (service != null) {
                jsonObject.addProperty(SERVICE_KEY, service);
            }
            if (target_span != null) {
                jsonObject.addProperty(TARGET_SPAN_KEY, target_span);
            }
            if (tags != null) {
                jsonObject.add(TAGS_KEY, MapUtils.getAsJsonObject(tags));
            }
            return jsonObject.toString();
        }
    }
}
