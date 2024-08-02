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
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents list of Span Sampling Rules read from JSON. See SPAN_SAMPLING_RULES
 */
public class SpanSamplingRules {
    public static final SpanSamplingRules EMPTY = new SpanSamplingRules(Collections.emptyList());
    private static final Logger log = LoggerFactory.getLogger(SpanSamplingRules.class);
    private final List<Rule> rules;

    public SpanSamplingRules(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(rules);
    }

    public static SpanSamplingRules deserialize(String json) {
        SpanSamplingRules result = EMPTY;
        try {
            result = filterOutNullRules(deserializeRules(new JsonReader(new StringReader(json))));
        } catch (Throwable ex) {
            log.error("Couldn't parse Span Sampling Rules from JSON: {}", json, ex);
        }
        return result;
    }

    public static SpanSamplingRules deserializeFile(String jsonFile) {
        SpanSamplingRules result = EMPTY;
        try (JsonReader reader = new JsonReader(new FileReader(jsonFile))) {
            result = filterOutNullRules(deserializeRules(reader));
        } catch (FileNotFoundException e) {
            log.warn("Span Sampling Rules file {} doesn't exist", jsonFile);
        } catch (IOException e) {
            log.error("Couldn't read Span Sampling Rules file {}.", jsonFile, e);
        } catch (Throwable ex) {
            log.error("Couldn't parse Span Sampling Rules from JSON file {}.", jsonFile, ex);
        }
        return result;
    }

    private static List<Rule> deserializeRules(JsonReader reader) throws IllegalStateException {
        final JsonArray rulesAsJsonArray = JsonParser.parseReader(reader).getAsJsonArray();
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

    private static SpanSamplingRules filterOutNullRules(List<Rule> rules) {
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
        return new SpanSamplingRules(notNullRules);
    }

    public List<Rule> getRules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public static final class Rule implements SamplingRule.SpanSamplingRule {
        private final String service;
        private final String name;
        private final String resource;
        private final Map<String, String> tags;
        private final double sampleRate;
        private final int maxPerSecond;

        private Rule(
                String service,
                String name,
                String resource,
                Map<String, String> tags,
                double sampleRate,
                int maxPerSecond) {
            this.service = service;
            this.name = name;
            this.resource = resource;
            this.tags = tags;
            this.sampleRate = sampleRate;
            this.maxPerSecond = maxPerSecond;
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
            int maxPerSecond = Integer.MAX_VALUE;
            if (jsonRule.max_per_second != null) {
                try {
                    double parsedMaxPerSeconds = Double.parseDouble(jsonRule.max_per_second);
                    if (parsedMaxPerSeconds <= 0) {
                        logRuleError(jsonRule, "max_per_second must be greater than 0.0");
                        return null;
                    }
                    maxPerSecond = Math.max((int) parsedMaxPerSeconds, 1);
                } catch (NumberFormatException ex) {
                    logRuleError(jsonRule, "max_per_second must be a number greater than 0.0");
                    return null;
                }
            }
            return new Rule(service, name, resource, tags, sampleRate, maxPerSecond);
        }

        private static void logRuleError(JsonRule rule, String error) {
            log.error("Skipping invalid Span Sampling Rule: {} - {}", rule, error);
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

        @Override
        public int getMaxPerSecond() {
            return maxPerSecond;
        }

        public String asStringJsonRule() {
            final JsonRule jsonRule = new JsonRule();
            jsonRule.service = service;
            jsonRule.name = name;
            jsonRule.resource = resource;
            jsonRule.tags = tags;
            jsonRule.sample_rate = String.valueOf(sampleRate);
            jsonRule.max_per_second = String.valueOf(maxPerSecond);
            return jsonRule.toString();
        }
    }

    private static final class JsonRule {
        static final String SERVICE_KEY = "service";
        static final String NAME_KEY = "name";
        static final String RESOURCE_KEY = "resource";
        static final String TAGS_KEY = "tags";
        static final String MAX_PER_SECOND_KEY = "max_per_second";
        static final String SAMPLE_RATE_KEY = "sample_rate";

        private static JsonRule deserializeRule(JsonObject ruleAsJsonObject) {
            final JsonRule rule = new JsonRule();
            rule.name = JsonObjectUtils.getAsString(ruleAsJsonObject, NAME_KEY);
            rule.resource = JsonObjectUtils.getAsString(ruleAsJsonObject, RESOURCE_KEY);
            rule.sample_rate = JsonObjectUtils.getAsString(ruleAsJsonObject, SAMPLE_RATE_KEY);
            rule.service = JsonObjectUtils.getAsString(ruleAsJsonObject, SERVICE_KEY);
            rule.max_per_second = JsonObjectUtils.getAsString(ruleAsJsonObject, MAX_PER_SECOND_KEY);
            rule.tags = JsonObjectUtils.safeGetAsMap(ruleAsJsonObject, TAGS_KEY);
            return rule;
        }

        String service;
        String name;
        String resource;
        Map<String, String> tags;
        String sample_rate; // Use String to be able to map int as double
        String max_per_second; // Use String to be able to map int as double

        @NonNull
        @Override
        public String toString() {
            final JsonObject jsonObject = new JsonObject();
            if (max_per_second != null) {
                jsonObject.addProperty(MAX_PER_SECOND_KEY, max_per_second);
            }
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
            if (tags != null) {
                jsonObject.add(TAGS_KEY, MapUtils.getAsJsonObject(tags));
            }
            return jsonObject.toString();
        }
    }

}
