package com.datadog.trace.api;

import static com.datadog.trace.api.ConfigDefaults.DEFAULT_AGENT_HOST;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_AGENT_TIMEOUT;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_AGENT_WRITER_TYPE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_CLIENT_IP_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_CLOCK_SYNC_PERIOD;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DATA_STREAMS_BUCKET_DURATION;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DATA_STREAMS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DB_CLIENT_HOST_SPLIT_BY_HOST;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE_TYPE_SUFFIX;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DB_DBM_PROPAGATION_MODE_MODE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_DOGSTATSD_START_DELAY;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_ELASTICSEARCH_BODY_AND_PARAMS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_ELASTICSEARCH_BODY_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_ELASTICSEARCH_PARAMS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_GRPC_CLIENT_ERROR_STATUSES;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_GRPC_SERVER_ERROR_STATUSES;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HEALTH_METRICS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_CLIENT_ERROR_STATUSES;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_CLIENT_SPLIT_BY_DOMAIN;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_SERVER_ERROR_STATUSES;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_SERVER_ROUTE_BASED_NAMING;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_HTTP_SERVER_TAG_QUERY_STRING;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_JAX_RS_EXCEPTION_AS_ERROR_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_LOGS_INJECTION_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_PARTIAL_FLUSH_MIN_SPANS;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_PERF_METRICS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_PRIORITY_SAMPLING_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_PRIORITY_SAMPLING_FORCE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_PROPAGATION_EXTRACT_LOG_HEADER_NAMES_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_INTEGRITY_CHECK_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_MAX_PAYLOAD_SIZE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_POLL_INTERVAL_SECONDS;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_TARGETS_KEY;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_REMOTE_CONFIG_TARGETS_KEY_ID;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SCOPE_DEPTH_LIMIT;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SCOPE_ITERATION_KEEP_ALIVE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SECURE_RANDOM;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SERVICE_NAME;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SERVLET_ROOT_CONTEXT_SERVICE_NAME;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SITE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_SPARK_TASK_HISTOGRAM_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_STARTUP_LOGS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_DEBUG_REQUESTS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_DEPENDENCY_COLLECTION_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_EXTENDED_HEARTBEAT_INTERVAL;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_HEARTBEAT_INTERVAL;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_LOG_COLLECTION_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TELEMETRY_METRICS_INTERVAL;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_128_BIT_TRACEID_GENERATION_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_AGENT_PORT;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_AGENT_V05_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_ANALYTICS_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_FLUSH_INTERVAL;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_HTTP_RESOURCE_REMOVE_TRAILING_SLASH;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_LONG_RUNNING_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_LONG_RUNNING_FLUSH_INTERVAL;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_PROPAGATION_EXTRACT_FIRST;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_PROPAGATION_STYLE;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_RATE_LIMIT;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_REPORT_HOSTNAME;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_RESOLVER_ENABLED;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_TRACE_X_DATADOG_TAGS_MAX_LENGTH;
import static com.datadog.trace.api.ConfigDefaults.DEFAULT_WRITER_BAGGAGE_INJECT;
import static com.datadog.trace.api.DDTags.INTERNAL_HOST_NAME;
import static com.datadog.trace.api.DDTags.LANGUAGE_TAG_KEY;
import static com.datadog.trace.api.DDTags.LANGUAGE_TAG_VALUE;
import static com.datadog.trace.api.DDTags.PID_TAG;
import static com.datadog.trace.api.DDTags.PROFILING_ENABLED;
import static com.datadog.trace.api.DDTags.RUNTIME_ID_TAG;
import static com.datadog.trace.api.DDTags.SCHEMA_VERSION_TAG_KEY;
import static com.datadog.trace.api.DDTags.SERVICE;
import static com.datadog.trace.api.config.CrashTrackingConfig.CRASH_TRACKING_AGENTLESS;
import static com.datadog.trace.api.config.CrashTrackingConfig.CRASH_TRACKING_AGENTLESS_DEFAULT;
import static com.datadog.trace.api.config.CrashTrackingConfig.CRASH_TRACKING_TAGS;
import static com.datadog.trace.api.config.GeneralConfig.AZURE_APP_SERVICES;
import static com.datadog.trace.api.config.GeneralConfig.DATA_STREAMS_BUCKET_DURATION_SECONDS;
import static com.datadog.trace.api.config.GeneralConfig.DATA_STREAMS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.DOGSTATSD_ARGS;
import static com.datadog.trace.api.config.GeneralConfig.DOGSTATSD_NAMED_PIPE;
import static com.datadog.trace.api.config.GeneralConfig.DOGSTATSD_PATH;
import static com.datadog.trace.api.config.GeneralConfig.DOGSTATSD_START_DELAY;
import static com.datadog.trace.api.config.GeneralConfig.ENV;
import static com.datadog.trace.api.config.GeneralConfig.GLOBAL_TAGS;
import static com.datadog.trace.api.config.GeneralConfig.HEALTH_METRICS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.HEALTH_METRICS_STATSD_HOST;
import static com.datadog.trace.api.config.GeneralConfig.HEALTH_METRICS_STATSD_PORT;
import static com.datadog.trace.api.config.GeneralConfig.PERF_METRICS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.PRIMARY_TAG;
import static com.datadog.trace.api.config.GeneralConfig.RUNTIME_ID_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.RUNTIME_METRICS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.SERVICE_NAME;
import static com.datadog.trace.api.config.GeneralConfig.SITE;
import static com.datadog.trace.api.config.GeneralConfig.STARTUP_LOGS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.STATSD_CLIENT_QUEUE_SIZE;
import static com.datadog.trace.api.config.GeneralConfig.STATSD_CLIENT_SOCKET_BUFFER;
import static com.datadog.trace.api.config.GeneralConfig.STATSD_CLIENT_SOCKET_TIMEOUT;
import static com.datadog.trace.api.config.GeneralConfig.TAGS;
import static com.datadog.trace.api.config.GeneralConfig.TELEMETRY_DEPENDENCY_COLLECTION_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.TELEMETRY_EXTENDED_HEARTBEAT_INTERVAL;
import static com.datadog.trace.api.config.GeneralConfig.TELEMETRY_HEARTBEAT_INTERVAL;
import static com.datadog.trace.api.config.GeneralConfig.TELEMETRY_LOG_COLLECTION_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.TELEMETRY_METRICS_INTERVAL;
import static com.datadog.trace.api.config.GeneralConfig.TRACER_METRICS_BUFFERING_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.TRACER_METRICS_ENABLED;
import static com.datadog.trace.api.config.GeneralConfig.TRACER_METRICS_MAX_AGGREGATES;
import static com.datadog.trace.api.config.GeneralConfig.TRACER_METRICS_MAX_PENDING;
import static com.datadog.trace.api.config.GeneralConfig.TRACE_DEBUG;
import static com.datadog.trace.api.config.GeneralConfig.TRACE_TRIAGE;
import static com.datadog.trace.api.config.GeneralConfig.VERSION;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_AGENTLESS;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_AGENTLESS_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_DATADOG_PROFILER_ENABLED;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_DIRECT_ALLOCATION_SAMPLE_LIMIT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_DIRECT_ALLOCATION_SAMPLE_LIMIT_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_HISTOGRAM_MAX_COLLECTION_SIZE;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_HISTOGRAM_MAX_COLLECTION_SIZE_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_HISTOGRAM_TOP_ITEMS;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_HISTOGRAM_TOP_ITEMS_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_RECORD_MESSAGE;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_RECORD_MESSAGE_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_SAMPLE_LIMIT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCEPTION_SAMPLE_LIMIT_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_EXCLUDE_AGENT_THREADS;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_PROXY_HOST;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_PROXY_PASSWORD;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_PROXY_PORT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_PROXY_PORT_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_PROXY_USERNAME;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_START_DELAY;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_START_DELAY_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_START_FORCE_FIRST;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_START_FORCE_FIRST_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_TAGS;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_TEMPLATE_OVERRIDE_FILE;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_COMPRESSION;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_COMPRESSION_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_PERIOD;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_PERIOD_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_SUMMARY_ON_413;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_SUMMARY_ON_413_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_TIMEOUT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_UPLOAD_TIMEOUT_DEFAULT;
import static com.datadog.trace.api.config.ProfilingConfig.PROFILING_URL;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_ENABLED;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_INTEGRITY_CHECK_ENABLED;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_MAX_PAYLOAD_SIZE;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_POLL_INTERVAL_SECONDS;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_TARGETS_KEY;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_TARGETS_KEY_ID;
import static com.datadog.trace.api.config.RemoteConfigConfig.REMOTE_CONFIG_URL;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_HOST;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_INSTANCE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_INSTANCE_TYPE_SUFFIX;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.DB_DBM_PROPAGATION_MODE_MODE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.ELASTICSEARCH_BODY_AND_PARAMS_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.ELASTICSEARCH_BODY_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.ELASTICSEARCH_PARAMS_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GOOGLE_PUBSUB_IGNORED_GRPC_METHODS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GRPC_CLIENT_ERROR_STATUSES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GRPC_IGNORED_INBOUND_METHODS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GRPC_IGNORED_OUTBOUND_METHODS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GRPC_SERVER_ERROR_STATUSES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.GRPC_SERVER_TRIM_PACKAGE_RESOURCE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_CLIENT_TAG_HEADERS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_CLIENT_TAG_QUERY_STRING;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_SERVER_DECODED_RESOURCE_PRESERVE_SPACES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_SERVER_RAW_QUERY_STRING;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_SERVER_RAW_RESOURCE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_SERVER_ROUTE_BASED_NAMING;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HTTP_SERVER_TAG_QUERY_STRING;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HYSTRIX_MEASURED_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.HYSTRIX_TAGS_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.IGNITE_CACHE_INCLUDE_KEYS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.INTEGRATION_SYNAPSE_LEGACY_OPERATION_NAME;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.JAX_RS_EXCEPTION_AS_ERROR_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.JMS_PROPAGATION_DISABLED_QUEUES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.JMS_PROPAGATION_DISABLED_TOPICS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.JMS_UNACKNOWLEDGED_MAX_AGE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.KAFKA_CLIENT_BASE64_DECODING_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.KAFKA_CLIENT_PROPAGATION_DISABLED_TOPICS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.LOGS_INJECTION_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.MESSAGE_BROKER_SPLIT_BY_DESTINATION;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.OBFUSCATION_QUERY_STRING_REGEXP;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.PLAY_REPORT_HTTP_STATUS;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.RABBIT_INCLUDE_ROUTINGKEY_IN_RESOURCE;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.RABBIT_PROPAGATION_DISABLED_EXCHANGES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.RABBIT_PROPAGATION_DISABLED_QUEUES;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.SERVLET_ASYNC_TIMEOUT_ERROR;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.SERVLET_PRINCIPAL_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.SERVLET_ROOT_CONTEXT_SERVICE_NAME;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.SPARK_TASK_HISTOGRAM_ENABLED;
import static com.datadog.trace.api.config.TraceInstrumentationConfig.SPRING_DATA_REPOSITORY_INTERFACE_RESOURCE_NAME;
import static com.datadog.trace.api.config.TracerConfig.AGENT_HOST;
import static com.datadog.trace.api.config.TracerConfig.AGENT_NAMED_PIPE;
import static com.datadog.trace.api.config.TracerConfig.AGENT_PORT_LEGACY;
import static com.datadog.trace.api.config.TracerConfig.AGENT_TIMEOUT;
import static com.datadog.trace.api.config.TracerConfig.BAGGAGE_MAPPING;
import static com.datadog.trace.api.config.TracerConfig.CLIENT_IP_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.CLOCK_SYNC_PERIOD;
import static com.datadog.trace.api.config.TracerConfig.ENABLE_TRACE_AGENT_V05;
import static com.datadog.trace.api.config.TracerConfig.HEADER_TAGS;
import static com.datadog.trace.api.config.TracerConfig.HTTP_CLIENT_ERROR_STATUSES;
import static com.datadog.trace.api.config.TracerConfig.HTTP_SERVER_ERROR_STATUSES;
import static com.datadog.trace.api.config.TracerConfig.ID_GENERATION_STRATEGY;
import static com.datadog.trace.api.config.TracerConfig.PARTIAL_FLUSH_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.PARTIAL_FLUSH_MIN_SPANS;
import static com.datadog.trace.api.config.TracerConfig.PRIORITY_SAMPLING;
import static com.datadog.trace.api.config.TracerConfig.PRIORITY_SAMPLING_FORCE;
import static com.datadog.trace.api.config.TracerConfig.PROPAGATION_EXTRACT_LOG_HEADER_NAMES_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.PROXY_NO_PROXY;
import static com.datadog.trace.api.config.TracerConfig.REQUEST_HEADER_TAGS;
import static com.datadog.trace.api.config.TracerConfig.REQUEST_HEADER_TAGS_COMMA_ALLOWED;
import static com.datadog.trace.api.config.TracerConfig.RESPONSE_HEADER_TAGS;
import static com.datadog.trace.api.config.TracerConfig.SCOPE_DEPTH_LIMIT;
import static com.datadog.trace.api.config.TracerConfig.SCOPE_INHERIT_ASYNC_PROPAGATION;
import static com.datadog.trace.api.config.TracerConfig.SCOPE_ITERATION_KEEP_ALIVE;
import static com.datadog.trace.api.config.TracerConfig.SCOPE_STRICT_MODE;
import static com.datadog.trace.api.config.TracerConfig.SECURE_RANDOM;
import static com.datadog.trace.api.config.TracerConfig.SERVICE_MAPPING;
import static com.datadog.trace.api.config.TracerConfig.SPAN_SAMPLING_RULES;
import static com.datadog.trace.api.config.TracerConfig.SPAN_SAMPLING_RULES_FILE;
import static com.datadog.trace.api.config.TracerConfig.SPAN_TAGS;
import static com.datadog.trace.api.config.TracerConfig.SPLIT_BY_TAGS;
import static com.datadog.trace.api.config.TracerConfig.TRACE_128_BIT_TRACEID_GENERATION_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_AGENT_ARGS;
import static com.datadog.trace.api.config.TracerConfig.TRACE_AGENT_PATH;
import static com.datadog.trace.api.config.TracerConfig.TRACE_AGENT_PORT;
import static com.datadog.trace.api.config.TracerConfig.TRACE_AGENT_URL;
import static com.datadog.trace.api.config.TracerConfig.TRACE_ANALYTICS_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_CLIENT_IP_HEADER;
import static com.datadog.trace.api.config.TracerConfig.TRACE_CLIENT_IP_RESOLVER_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_GIT_METADATA_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_HTTP_CLIENT_PATH_RESOURCE_NAME_MAPPING;
import static com.datadog.trace.api.config.TracerConfig.TRACE_HTTP_RESOURCE_REMOVE_TRAILING_SLASH;
import static com.datadog.trace.api.config.TracerConfig.TRACE_HTTP_SERVER_PATH_RESOURCE_NAME_MAPPING;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PEER_SERVICE_COMPONENT_OVERRIDES;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PEER_SERVICE_DEFAULTS_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PEER_SERVICE_MAPPING;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PROPAGATION_EXTRACT_FIRST;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PROPAGATION_STYLE;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PROPAGATION_STYLE_EXTRACT;
import static com.datadog.trace.api.config.TracerConfig.TRACE_PROPAGATION_STYLE_INJECT;
import static com.datadog.trace.api.config.TracerConfig.TRACE_RATE_LIMIT;
import static com.datadog.trace.api.config.TracerConfig.TRACE_REMOVE_INTEGRATION_SERVICE_NAMES_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_REPORT_HOSTNAME;
import static com.datadog.trace.api.config.TracerConfig.TRACE_RESOLVER_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_SAMPLE_RATE;
import static com.datadog.trace.api.config.TracerConfig.TRACE_SAMPLING_OPERATION_RULES;
import static com.datadog.trace.api.config.TracerConfig.TRACE_SAMPLING_RULES;
import static com.datadog.trace.api.config.TracerConfig.TRACE_SAMPLING_SERVICE_RULES;
import static com.datadog.trace.api.config.TracerConfig.TRACE_SPAN_ATTRIBUTE_SCHEMA;
import static com.datadog.trace.api.config.TracerConfig.TRACE_STRICT_WRITES_ENABLED;
import static com.datadog.trace.api.config.TracerConfig.TRACE_X_DATADOG_TAGS_MAX_LENGTH;
import static com.datadog.trace.api.config.TracerConfig.WRITER_BAGGAGE_INJECT;
import static com.datadog.trace.api.config.TracerConfig.WRITER_TYPE;
import static com.datadog.trace.util.CollectionUtils.tryMakeImmutableSet;

import androidx.annotation.NonNull;

import com.datadog.android.trace.internal.compat.function.Function;
import com.datadog.trace.api.config.GeneralConfig;
import com.datadog.trace.api.config.ProfilingConfig;
import com.datadog.trace.api.config.TracerConfig;
import com.datadog.trace.api.naming.SpanNaming;
import com.datadog.trace.bootstrap.config.provider.ConfigProvider;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import com.datadog.trace.util.PidHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Config reads values with the following priority:
 *
 * <p>1) system properties
 *
 * <p>2) environment variables,
 *
 * <p>3) optional configuration file
 *
 * <p>4) platform dependant properties. It also includes default values to ensure a valid config.
 *
 * <p>System properties are {@link Config#PREFIX}'ed. Environment variables are the same as the
 * system property, but uppercased and '.' is replaced with '_'.
 *
 * @see ConfigProvider for details on how configs are processed
 * @see InstrumenterConfig for pre-instrumentation configurations
 * @see DynamicConfig for configuration that can be dynamically updated via remote-config
 */
public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final Pattern COLON = Pattern.compile(":");

    private final InstrumenterConfig instrumenterConfig;

    private final long startTimeMillis = System.currentTimeMillis();
    private final boolean timelineEventsEnabled;

    /**
     * this is a random UUID that gets generated on JVM start up and is attached to every root span
     * and every JMX metric that is sent out.
     */
    static class RuntimeIdHolder {
        static final String runtimeId = UUID.randomUUID().toString();
    }

    static class HostNameHolder {
        static final String hostName = initHostName();
    }

    private final boolean runtimeIdEnabled;

    /**
     * This is the version of the runtime, ex: 1.8.0_332, 11.0.15, 17.0.3
     */
    private final String runtimeVersion;

    /**
     * Note: this has effect only on profiling site. Traces are sent to Datadog agent and are not
     * affected by this setting.
     */
    private final String site;

    private final String serviceName;
    private final boolean serviceNameSetByUser;
    private final String rootContextServiceName;
    private final boolean integrationSynapseLegacyOperationName;
    private final String writerType;
    private final boolean injectBaggageAsTagsEnabled;
    private final boolean agentConfiguredUsingDefault;
    private final String agentUrl;
    private final String agentHost;
    private final int agentPort;
    private final String agentNamedPipe;
    private final int agentTimeout;
    private final Set<String> noProxyHosts;
    private final boolean prioritySamplingEnabled;
    private final String prioritySamplingForce;
    private final boolean traceResolverEnabled;
    private final int spanAttributeSchemaVersion;
    private final boolean peerServiceDefaultsEnabled;
    private final Map<String, String> peerServiceComponentOverrides;
    private final boolean removeIntegrationServiceNamesEnabled;
    private final Map<String, String> peerServiceMapping;
    private final Map<String, String> serviceMapping;
    private final Map<String, String> tags;
    private final Map<String, String> spanTags;
    private final Map<String, String> requestHeaderTags;
    private final Map<String, String> responseHeaderTags;
    private final Map<String, String> baggageMapping;
    private final boolean requestHeaderTagsCommaAllowed;
    private final BitSet httpServerErrorStatuses;
    private final BitSet httpClientErrorStatuses;
    private final boolean httpServerTagQueryString;
    private final boolean httpServerRawQueryString;
    private final boolean httpServerRawResource;
    private final boolean httpServerDecodedResourcePreserveSpaces;
    private final boolean httpServerRouteBasedNaming;
    private final Map<String, String> httpServerPathResourceNameMapping;
    private final Map<String, String> httpClientPathResourceNameMapping;
    private final boolean httpResourceRemoveTrailingSlash;
    private final boolean httpClientTagQueryString;
    private final boolean httpClientTagHeaders;
    private final boolean httpClientSplitByDomain;
    private final boolean dbClientSplitByInstance;
    private final boolean dbClientSplitByInstanceTypeSuffix;
    private final boolean dbClientSplitByHost;
    private final Set<String> splitByTags;
    private final int scopeDepthLimit;
    private final boolean scopeStrictMode;
    private final boolean scopeInheritAsyncPropagation;
    private final int scopeIterationKeepAlive;
    private final int partialFlushMinSpans;
    private final boolean traceStrictWritesEnabled;
    private final boolean logExtractHeaderNames;
    private final boolean tracePropagationStyleB3PaddingEnabled;
    private final Set<TracePropagationStyle> tracePropagationStylesToExtract;
    private final Set<TracePropagationStyle> tracePropagationStylesToInject;
    private final boolean tracePropagationExtractFirst;
    private final int clockSyncPeriod;
    private final boolean logsInjectionEnabled;

    private final String dogStatsDNamedPipe;
    private final int dogStatsDStartDelay;

    private final Integer statsDClientQueueSize;
    private final Integer statsDClientSocketBuffer;
    private final Integer statsDClientSocketTimeout;

    private final boolean runtimeMetricsEnabled;

    // These values are default-ed to those of jmx fetch values as needed
    private final boolean healthMetricsEnabled;
    private final String healthMetricsStatsdHost;
    private final Integer healthMetricsStatsdPort;
    private final boolean perfMetricsEnabled;

    private final boolean tracerMetricsEnabled;
    private final boolean tracerMetricsBufferingEnabled;
    private final int tracerMetricsMaxAggregates;
    private final int tracerMetricsMaxPending;

    private final boolean reportHostName;

    private final boolean traceAnalyticsEnabled;
    private final String traceClientIpHeader;
    private final boolean traceClientIpResolverEnabled;

    private final boolean traceGitMetadataEnabled;

    private final Map<String, String> traceSamplingServiceRules;
    private final Map<String, String> traceSamplingOperationRules;
    private final String traceSamplingRules;
    private final Double traceSampleRate;
    private final int traceRateLimit;
    private final String spanSamplingRules;
    private final String spanSamplingRulesFile;

    private final boolean profilingEnabled;
    private final boolean profilingAgentless;
    private final boolean isDatadogProfilerEnabled;
    @Deprecated
    private final String profilingUrl;
    private final Map<String, String> profilingTags;
    private final int profilingStartDelay;
    private final boolean profilingStartForceFirst;
    private final int profilingUploadPeriod;
    private final String profilingTemplateOverrideFile;
    private final int profilingUploadTimeout;
    private final String profilingUploadCompression;
    private final String profilingProxyHost;
    private final int profilingProxyPort;
    private final String profilingProxyUsername;
    private final String profilingProxyPassword;
    private final int profilingExceptionSampleLimit;
    private final int profilingDirectAllocationSampleLimit;
    private final int profilingExceptionHistogramTopItems;
    private final int profilingExceptionHistogramMaxCollectionSize;
    private final boolean profilingExcludeAgentThreads;
    private final boolean profilingUploadSummaryOn413Enabled;
    private final boolean profilingRecordExceptionMessage;

    private final boolean crashTrackingAgentless;
    private final Map<String, String> crashTrackingTags;

    private final boolean clientIpEnabled;

    private final boolean remoteConfigEnabled;
    private final boolean remoteConfigIntegrityCheckEnabled;
    private final String remoteConfigUrl;
    private final float remoteConfigPollIntervalSeconds;
    private final long remoteConfigMaxPayloadSize;
    private final String remoteConfigTargetsKeyId;
    private final String remoteConfigTargetsKey;

    private final String DBMPropagationMode;

    private final boolean awsPropagationEnabled;
    private final boolean sqsPropagationEnabled;

    private final boolean kafkaClientPropagationEnabled;
    private final Set<String> kafkaClientPropagationDisabledTopics;
    private final boolean kafkaClientBase64DecodingEnabled;

    private final boolean jmsPropagationEnabled;
    private final Set<String> jmsPropagationDisabledTopics;
    private final Set<String> jmsPropagationDisabledQueues;
    private final int jmsUnacknowledgedMaxAge;

    private final boolean rabbitPropagationEnabled;
    private final Set<String> rabbitPropagationDisabledQueues;
    private final Set<String> rabbitPropagationDisabledExchanges;

    private final boolean rabbitIncludeRoutingKeyInResource;

    private final boolean messageBrokerSplitByDestination;

    private final boolean hystrixTagsEnabled;
    private final boolean hystrixMeasuredEnabled;

    private final boolean igniteCacheIncludeKeys;

    private final String obfuscationQueryRegexp;

    // TODO: remove at a future point.
    private final boolean playReportHttpStatus;

    private final boolean servletPrincipalEnabled;
    private final boolean servletAsyncTimeoutError;

    private final boolean springDataRepositoryInterfaceResourceName;

    private final int xDatadogTagsMaxLength;

    private final boolean traceAgentV05Enabled;

    private final boolean debugEnabled;
    private final boolean triageEnabled;
    private final boolean startupLogsEnabled;
    private final String configFileStatus;

    private final IdGenerationStrategy idGenerationStrategy;

    private final boolean secureRandom;

    private final boolean trace128bitTraceIdGenerationEnabled;

    private final Set<String> grpcIgnoredInboundMethods;
    private final Set<String> grpcIgnoredOutboundMethods;
    private final boolean grpcServerTrimPackageResource;
    private final BitSet grpcServerErrorStatuses;
    private final BitSet grpcClientErrorStatuses;

    private final boolean dataStreamsEnabled;
    private final float dataStreamsBucketDurationSeconds;

    private final float telemetryHeartbeatInterval;
    private final long telemetryExtendedHeartbeatInterval;
    private final float telemetryMetricsInterval;
    private final boolean isTelemetryDependencyServiceEnabled;
    private final boolean telemetryMetricsEnabled;
    private final boolean isTelemetryLogCollectionEnabled;

    private final boolean azureAppServices;
    private final String traceAgentPath;
    private final List<String> traceAgentArgs;
    private final String dogStatsDPath;
    private final List<String> dogStatsDArgs;

    private String env;
    private String version;
    private final String primaryTag;

    private final ConfigProvider configProvider;

    private final boolean longRunningTraceEnabled;
    private final long longRunningTraceFlushInterval;
    private final boolean elasticsearchBodyEnabled;
    private final boolean elasticsearchParamsEnabled;
    private final boolean elasticsearchBodyAndParamsEnabled;
    private final boolean sparkTaskHistogramEnabled;
    private final boolean jaxRsExceptionAsErrorsEnabled;

    private final float traceFlushIntervalSeconds;

    private final boolean telemetryDebugRequestsEnabled;

    // Read order: System Properties -> Env Variables, [-> properties file], [-> default value]
    private Config() {
        this(ConfigProvider.createDefault());
    }

    private Config(final ConfigProvider configProvider) {
        this(configProvider, new InstrumenterConfig(configProvider));
    }

    private Config(final ConfigProvider configProvider, final InstrumenterConfig instrumenterConfig) {
        this.configProvider = configProvider;
        this.instrumenterConfig = instrumenterConfig;
        configFileStatus = configProvider.getConfigFileStatus();
        runtimeIdEnabled = configProvider.getBoolean(RUNTIME_ID_ENABLED, true);
        runtimeVersion = System.getProperty("java.version", "unknown");
        site = configProvider.getString(SITE, DEFAULT_SITE);

        String userProvidedServiceName =
                configProvider.getString(SERVICE, null, SERVICE_NAME);

        if (userProvidedServiceName == null) {
            serviceNameSetByUser = false;
            serviceName = configProvider.getString(SERVICE, DEFAULT_SERVICE_NAME, SERVICE_NAME);
        } else {
            serviceNameSetByUser = true;
            serviceName = userProvidedServiceName;
        }

        rootContextServiceName =
                configProvider.getString(
                        SERVLET_ROOT_CONTEXT_SERVICE_NAME, DEFAULT_SERVLET_ROOT_CONTEXT_SERVICE_NAME);

        integrationSynapseLegacyOperationName =
                configProvider.getBoolean(INTEGRATION_SYNAPSE_LEGACY_OPERATION_NAME, false);
        writerType = configProvider.getString(WRITER_TYPE, DEFAULT_AGENT_WRITER_TYPE);
        injectBaggageAsTagsEnabled =
                configProvider.getBoolean(WRITER_BAGGAGE_INJECT, DEFAULT_WRITER_BAGGAGE_INJECT);
        secureRandom = configProvider.getBoolean(SECURE_RANDOM, DEFAULT_SECURE_RANDOM);
        elasticsearchBodyEnabled =
                configProvider.getBoolean(ELASTICSEARCH_BODY_ENABLED, DEFAULT_ELASTICSEARCH_BODY_ENABLED);
        elasticsearchParamsEnabled =
                configProvider.getBoolean(
                        ELASTICSEARCH_PARAMS_ENABLED, DEFAULT_ELASTICSEARCH_PARAMS_ENABLED);
        elasticsearchBodyAndParamsEnabled =
                configProvider.getBoolean(
                        ELASTICSEARCH_BODY_AND_PARAMS_ENABLED, DEFAULT_ELASTICSEARCH_BODY_AND_PARAMS_ENABLED);
        String strategyName = configProvider.getString(ID_GENERATION_STRATEGY);
        trace128bitTraceIdGenerationEnabled =
                configProvider.getBoolean(
                        TRACE_128_BIT_TRACEID_GENERATION_ENABLED,
                        DEFAULT_TRACE_128_BIT_TRACEID_GENERATION_ENABLED);
        if (secureRandom) {
            strategyName = "SECURE_RANDOM";
        }
        if (strategyName == null) {
            strategyName = "RANDOM";
        }
        IdGenerationStrategy strategy =
                IdGenerationStrategy.fromName(strategyName, trace128bitTraceIdGenerationEnabled);
        if (strategy == null) {
            log.warn(
                    "*** you are trying to use an unknown id generation strategy {} - falling back to RANDOM",
                    strategyName);
            strategyName = "RANDOM";
            strategy = IdGenerationStrategy.fromName(strategyName, trace128bitTraceIdGenerationEnabled);
        }
        if (!strategyName.equals("RANDOM") && !strategyName.equals("SECURE_RANDOM")) {
            log.warn(
                    "*** you are using an unsupported id generation strategy {} - this can impact correctness of traces",
                    strategyName);
        }
        idGenerationStrategy = strategy;

        String agentHostFromEnvironment = null;
        int agentPortFromEnvironment = -1;
        boolean rebuildAgentUrl = false;

        final String agentUrlFromEnvironment = configProvider.getString(TRACE_AGENT_URL);
        if (agentUrlFromEnvironment != null) {
            try {
                final URI parsedAgentUrl = new URI(agentUrlFromEnvironment);
                agentHostFromEnvironment = parsedAgentUrl.getHost();
                agentPortFromEnvironment = parsedAgentUrl.getPort();
            } catch (URISyntaxException e) {
                log.warn("{} not configured correctly: {}. Ignoring", TRACE_AGENT_URL, e.getMessage());
            }
        }

        if (agentHostFromEnvironment == null) {
            agentHostFromEnvironment = configProvider.getString(AGENT_HOST);
            rebuildAgentUrl = true;
        }

        if (agentPortFromEnvironment < 0) {
            agentPortFromEnvironment = configProvider.getInteger(TRACE_AGENT_PORT, -1, AGENT_PORT_LEGACY);
            rebuildAgentUrl = true;
        }

        if (agentHostFromEnvironment == null) {
            agentHost = DEFAULT_AGENT_HOST;
        } else {
            agentHost = agentHostFromEnvironment;
        }

        if (agentPortFromEnvironment < 0) {
            agentPort = DEFAULT_TRACE_AGENT_PORT;
        } else {
            agentPort = agentPortFromEnvironment;
        }

        if (rebuildAgentUrl) {
            agentUrl = "http://" + agentHost + ":" + agentPort;
        } else {
            agentUrl = agentUrlFromEnvironment;
        }

        agentNamedPipe = configProvider.getString(AGENT_NAMED_PIPE);

        agentConfiguredUsingDefault =
                agentHostFromEnvironment == null
                        && agentPortFromEnvironment < 0
                        && agentNamedPipe == null;

        agentTimeout = configProvider.getInteger(AGENT_TIMEOUT, DEFAULT_AGENT_TIMEOUT);

        // DD_PROXY_NO_PROXY is specified as a space-separated list of hosts
        noProxyHosts = tryMakeImmutableSet(configProvider.getSpacedList(PROXY_NO_PROXY));

        prioritySamplingEnabled =
                configProvider.getBoolean(PRIORITY_SAMPLING, DEFAULT_PRIORITY_SAMPLING_ENABLED);
        prioritySamplingForce =
                configProvider.getString(PRIORITY_SAMPLING_FORCE, DEFAULT_PRIORITY_SAMPLING_FORCE);

        traceResolverEnabled =
                configProvider.getBoolean(TRACE_RESOLVER_ENABLED, DEFAULT_TRACE_RESOLVER_ENABLED);
        serviceMapping = configProvider.getMergedMap(SERVICE_MAPPING);

        {
            final Map<String, String> tags = new HashMap<>(configProvider.getMergedMap(GLOBAL_TAGS));
            tags.putAll(configProvider.getMergedMap(TAGS));
            this.tags = getMapWithPropertiesDefinedByEnvironment(tags, ENV, VERSION);
        }

        spanTags = configProvider.getMergedMap(SPAN_TAGS);

        primaryTag = configProvider.getString(PRIMARY_TAG);

        if (isEnabled(false, HEADER_TAGS, ".legacy.parsing.enabled")) {
            requestHeaderTags = configProvider.getMergedMap(HEADER_TAGS);
            responseHeaderTags = Collections.emptyMap();
            if (configProvider.isSet(REQUEST_HEADER_TAGS)) {
                logIgnoredSettingWarning(REQUEST_HEADER_TAGS, HEADER_TAGS, ".legacy.parsing.enabled");
            }
            if (configProvider.isSet(RESPONSE_HEADER_TAGS)) {
                logIgnoredSettingWarning(RESPONSE_HEADER_TAGS, HEADER_TAGS, ".legacy.parsing.enabled");
            }
        } else {
            requestHeaderTags =
                    configProvider.getMergedMapWithOptionalMappings(
                            "http.request.headers.", true, HEADER_TAGS, REQUEST_HEADER_TAGS);
            responseHeaderTags =
                    configProvider.getMergedMapWithOptionalMappings(
                            "http.response.headers.", true, HEADER_TAGS, RESPONSE_HEADER_TAGS);
        }
        requestHeaderTagsCommaAllowed =
                configProvider.getBoolean(REQUEST_HEADER_TAGS_COMMA_ALLOWED, true);

        baggageMapping = configProvider.getMergedMap(BAGGAGE_MAPPING);

        spanAttributeSchemaVersion = schemaVersionFromConfig();

        // following two only used in v0.
        // in v1+ defaults are always calculated regardless this feature flag
        peerServiceDefaultsEnabled =
                configProvider.getBoolean(TRACE_PEER_SERVICE_DEFAULTS_ENABLED, false);
        peerServiceComponentOverrides =
                configProvider.getMergedMap(TRACE_PEER_SERVICE_COMPONENT_OVERRIDES);
        // feature flag to remove fake services in v0
        removeIntegrationServiceNamesEnabled =
                configProvider.getBoolean(TRACE_REMOVE_INTEGRATION_SERVICE_NAMES_ENABLED, false);

        peerServiceMapping = configProvider.getMergedMap(TRACE_PEER_SERVICE_MAPPING);

        httpServerPathResourceNameMapping =
                configProvider.getOrderedMap(TRACE_HTTP_SERVER_PATH_RESOURCE_NAME_MAPPING);

        httpClientPathResourceNameMapping =
                configProvider.getOrderedMap(TRACE_HTTP_CLIENT_PATH_RESOURCE_NAME_MAPPING);

        httpResourceRemoveTrailingSlash =
                configProvider.getBoolean(
                        TRACE_HTTP_RESOURCE_REMOVE_TRAILING_SLASH,
                        DEFAULT_TRACE_HTTP_RESOURCE_REMOVE_TRAILING_SLASH);

        httpServerErrorStatuses =
                configProvider.getIntegerRange(
                        HTTP_SERVER_ERROR_STATUSES, DEFAULT_HTTP_SERVER_ERROR_STATUSES);

        httpClientErrorStatuses =
                configProvider.getIntegerRange(
                        HTTP_CLIENT_ERROR_STATUSES, DEFAULT_HTTP_CLIENT_ERROR_STATUSES);

        httpServerTagQueryString =
                configProvider.getBoolean(
                        HTTP_SERVER_TAG_QUERY_STRING, DEFAULT_HTTP_SERVER_TAG_QUERY_STRING);

        httpServerRawQueryString = configProvider.getBoolean(HTTP_SERVER_RAW_QUERY_STRING, true);

        httpServerRawResource = configProvider.getBoolean(HTTP_SERVER_RAW_RESOURCE, false);

        httpServerDecodedResourcePreserveSpaces =
                configProvider.getBoolean(HTTP_SERVER_DECODED_RESOURCE_PRESERVE_SPACES, true);

        httpServerRouteBasedNaming =
                configProvider.getBoolean(
                        HTTP_SERVER_ROUTE_BASED_NAMING, DEFAULT_HTTP_SERVER_ROUTE_BASED_NAMING);

        httpClientTagQueryString =
                configProvider.getBoolean(
                        HTTP_CLIENT_TAG_QUERY_STRING, DEFAULT_HTTP_CLIENT_TAG_QUERY_STRING);

        httpClientTagHeaders = configProvider.getBoolean(HTTP_CLIENT_TAG_HEADERS, true);

        httpClientSplitByDomain =
                configProvider.getBoolean(
                        HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, DEFAULT_HTTP_CLIENT_SPLIT_BY_DOMAIN);

        dbClientSplitByInstance =
                configProvider.getBoolean(
                        DB_CLIENT_HOST_SPLIT_BY_INSTANCE, DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE);

        dbClientSplitByInstanceTypeSuffix =
                configProvider.getBoolean(
                        DB_CLIENT_HOST_SPLIT_BY_INSTANCE_TYPE_SUFFIX,
                        DEFAULT_DB_CLIENT_HOST_SPLIT_BY_INSTANCE_TYPE_SUFFIX);

        dbClientSplitByHost =
                configProvider.getBoolean(
                        DB_CLIENT_HOST_SPLIT_BY_HOST, DEFAULT_DB_CLIENT_HOST_SPLIT_BY_HOST);

        DBMPropagationMode =
                configProvider.getString(
                        DB_DBM_PROPAGATION_MODE_MODE, DEFAULT_DB_DBM_PROPAGATION_MODE_MODE);

        splitByTags = tryMakeImmutableSet(configProvider.getList(SPLIT_BY_TAGS));

        springDataRepositoryInterfaceResourceName =
                configProvider.getBoolean(SPRING_DATA_REPOSITORY_INTERFACE_RESOURCE_NAME, true);

        scopeDepthLimit = configProvider.getInteger(SCOPE_DEPTH_LIMIT, DEFAULT_SCOPE_DEPTH_LIMIT);

        scopeStrictMode = configProvider.getBoolean(SCOPE_STRICT_MODE, false);

        scopeInheritAsyncPropagation = configProvider.getBoolean(SCOPE_INHERIT_ASYNC_PROPAGATION, true);

        scopeIterationKeepAlive =
                configProvider.getInteger(SCOPE_ITERATION_KEEP_ALIVE, DEFAULT_SCOPE_ITERATION_KEEP_ALIVE);

        boolean partialFlushEnabled = configProvider.getBoolean(PARTIAL_FLUSH_ENABLED, true);
        partialFlushMinSpans =
                !partialFlushEnabled
                        ? 0
                        : configProvider.getInteger(PARTIAL_FLUSH_MIN_SPANS, DEFAULT_PARTIAL_FLUSH_MIN_SPANS);

        traceStrictWritesEnabled = configProvider.getBoolean(TRACE_STRICT_WRITES_ENABLED, false);

        logExtractHeaderNames =
                configProvider.getBoolean(
                        PROPAGATION_EXTRACT_LOG_HEADER_NAMES_ENABLED,
                        DEFAULT_PROPAGATION_EXTRACT_LOG_HEADER_NAMES_ENABLED);

        tracePropagationStyleB3PaddingEnabled =
                isEnabled(true, TRACE_PROPAGATION_STYLE, ".b3.padding.enabled");
        {
            Set<TracePropagationStyle> common =
                    getSettingsSetFromEnvironment(
                            TRACE_PROPAGATION_STYLE, TracePropagationStyle::valueOfDisplayName, false);
            Set<TracePropagationStyle> extract =
                    getSettingsSetFromEnvironment(
                            TRACE_PROPAGATION_STYLE_EXTRACT, TracePropagationStyle::valueOfDisplayName, false);
            Set<TracePropagationStyle> inject =
                    getSettingsSetFromEnvironment(
                            TRACE_PROPAGATION_STYLE_INJECT, TracePropagationStyle::valueOfDisplayName, false);
            String extractOrigin = TRACE_PROPAGATION_STYLE_EXTRACT;
            String injectOrigin = TRACE_PROPAGATION_STYLE_INJECT;
            // Check if we should use the common setting for extraction
            if (extract.isEmpty()) {
                extract = common;
                extractOrigin = TRACE_PROPAGATION_STYLE;
            } else if (!common.isEmpty()) {
                // The more specific settings will override the common setting, so log a warning
                logOverriddenSettingWarning(
                        TRACE_PROPAGATION_STYLE, TRACE_PROPAGATION_STYLE_EXTRACT, extract);
            }
            // Check if we should use the common setting for injection
            if (inject.isEmpty()) {
                inject = common;
            } else if (!common.isEmpty()) {
                // The more specific settings will override the common setting, so log a warning
                logOverriddenSettingWarning(
                        TRACE_PROPAGATION_STYLE, TRACE_PROPAGATION_STYLE_INJECT, inject);
            }
            // Now we can check if we should pick the default injection/extraction
            tracePropagationStylesToExtract =
                    extract.isEmpty() ? DEFAULT_TRACE_PROPAGATION_STYLE : extract;
            tracePropagationStylesToInject = inject.isEmpty() ? DEFAULT_TRACE_PROPAGATION_STYLE : inject;
        }

        tracePropagationExtractFirst =
                configProvider.getBoolean(
                        TRACE_PROPAGATION_EXTRACT_FIRST, DEFAULT_TRACE_PROPAGATION_EXTRACT_FIRST);

        clockSyncPeriod = configProvider.getInteger(CLOCK_SYNC_PERIOD, DEFAULT_CLOCK_SYNC_PERIOD);

        logsInjectionEnabled =
                configProvider.getBoolean(LOGS_INJECTION_ENABLED, DEFAULT_LOGS_INJECTION_ENABLED);

        dogStatsDNamedPipe = configProvider.getString(DOGSTATSD_NAMED_PIPE);

        dogStatsDStartDelay =
                configProvider.getInteger(
                        DOGSTATSD_START_DELAY, DEFAULT_DOGSTATSD_START_DELAY);

        statsDClientQueueSize = configProvider.getInteger(STATSD_CLIENT_QUEUE_SIZE);
        statsDClientSocketBuffer = configProvider.getInteger(STATSD_CLIENT_SOCKET_BUFFER);
        statsDClientSocketTimeout = configProvider.getInteger(STATSD_CLIENT_SOCKET_TIMEOUT);

        runtimeMetricsEnabled = configProvider.getBoolean(RUNTIME_METRICS_ENABLED, true);

        // Writer.Builder createMonitor will use the values of the JMX fetch & agent to fill-in defaults
        healthMetricsEnabled =
                runtimeMetricsEnabled
                        && configProvider.getBoolean(HEALTH_METRICS_ENABLED, DEFAULT_HEALTH_METRICS_ENABLED);
        healthMetricsStatsdHost = configProvider.getString(HEALTH_METRICS_STATSD_HOST);
        healthMetricsStatsdPort = configProvider.getInteger(HEALTH_METRICS_STATSD_PORT);
        perfMetricsEnabled =
                runtimeMetricsEnabled
                        && configProvider.getBoolean(PERF_METRICS_ENABLED, DEFAULT_PERF_METRICS_ENABLED);

        tracerMetricsEnabled = configProvider.getBoolean(TRACER_METRICS_ENABLED, false);
        tracerMetricsBufferingEnabled =
                configProvider.getBoolean(TRACER_METRICS_BUFFERING_ENABLED, false);
        tracerMetricsMaxAggregates = configProvider.getInteger(TRACER_METRICS_MAX_AGGREGATES, 2048);
        tracerMetricsMaxPending = configProvider.getInteger(TRACER_METRICS_MAX_PENDING, 2048);

        reportHostName =
                configProvider.getBoolean(TRACE_REPORT_HOSTNAME, DEFAULT_TRACE_REPORT_HOSTNAME);

        traceAgentV05Enabled =
                configProvider.getBoolean(ENABLE_TRACE_AGENT_V05, DEFAULT_TRACE_AGENT_V05_ENABLED);

        traceAnalyticsEnabled =
                configProvider.getBoolean(TRACE_ANALYTICS_ENABLED, DEFAULT_TRACE_ANALYTICS_ENABLED);

        String traceClientIpHeader = configProvider.getString(TRACE_CLIENT_IP_HEADER);
        if (traceClientIpHeader != null) {
            traceClientIpHeader = traceClientIpHeader.toLowerCase(Locale.ROOT);
        }
        this.traceClientIpHeader = traceClientIpHeader;

        traceClientIpResolverEnabled =
                configProvider.getBoolean(TRACE_CLIENT_IP_RESOLVER_ENABLED, true);

        traceGitMetadataEnabled = configProvider.getBoolean(TRACE_GIT_METADATA_ENABLED, true);

        traceSamplingServiceRules = configProvider.getMergedMap(TRACE_SAMPLING_SERVICE_RULES);
        traceSamplingOperationRules = configProvider.getMergedMap(TRACE_SAMPLING_OPERATION_RULES);
        traceSamplingRules = configProvider.getString(TRACE_SAMPLING_RULES);
        traceSampleRate = configProvider.getDouble(TRACE_SAMPLE_RATE);
        traceRateLimit = configProvider.getInteger(TRACE_RATE_LIMIT, DEFAULT_TRACE_RATE_LIMIT);
        spanSamplingRules = configProvider.getString(SPAN_SAMPLING_RULES);
        spanSamplingRulesFile = configProvider.getString(SPAN_SAMPLING_RULES_FILE);

        // For the native image 'instrumenterConfig.isProfilingEnabled()' value will be 'baked-in' based
        // on whether
        // the profiler was enabled at build time or not.
        // Otherwise just do the standard config lookup by key.
        profilingEnabled =
                configProvider.getBoolean(
                        ProfilingConfig.PROFILING_ENABLED, instrumenterConfig.isProfilingEnabled());
        profilingAgentless =
                configProvider.getBoolean(PROFILING_AGENTLESS, PROFILING_AGENTLESS_DEFAULT);
        isDatadogProfilerEnabled =
                !isDatadogProfilerEnablementOverridden()
                        && configProvider.getBoolean(
                        PROFILING_DATADOG_PROFILER_ENABLED, isDatadogProfilerSafeInCurrentEnvironment());
        profilingUrl = configProvider.getString(PROFILING_URL);

        profilingTags = configProvider.getMergedMap(PROFILING_TAGS);
        profilingStartDelay =
                configProvider.getInteger(PROFILING_START_DELAY, PROFILING_START_DELAY_DEFAULT);
        profilingStartForceFirst =
                configProvider.getBoolean(PROFILING_START_FORCE_FIRST, PROFILING_START_FORCE_FIRST_DEFAULT);
        profilingUploadPeriod =
                configProvider.getInteger(PROFILING_UPLOAD_PERIOD, PROFILING_UPLOAD_PERIOD_DEFAULT);
        profilingTemplateOverrideFile = configProvider.getString(PROFILING_TEMPLATE_OVERRIDE_FILE);
        profilingUploadTimeout =
                configProvider.getInteger(PROFILING_UPLOAD_TIMEOUT, PROFILING_UPLOAD_TIMEOUT_DEFAULT);
        profilingUploadCompression =
                configProvider.getString(
                        PROFILING_UPLOAD_COMPRESSION, PROFILING_UPLOAD_COMPRESSION_DEFAULT);
        profilingProxyHost = configProvider.getString(PROFILING_PROXY_HOST);
        profilingProxyPort =
                configProvider.getInteger(PROFILING_PROXY_PORT, PROFILING_PROXY_PORT_DEFAULT);
        profilingProxyUsername = configProvider.getString(PROFILING_PROXY_USERNAME);
        profilingProxyPassword = configProvider.getString(PROFILING_PROXY_PASSWORD);

        profilingExceptionSampleLimit =
                configProvider.getInteger(
                        PROFILING_EXCEPTION_SAMPLE_LIMIT, PROFILING_EXCEPTION_SAMPLE_LIMIT_DEFAULT);
        profilingDirectAllocationSampleLimit =
                configProvider.getInteger(
                        PROFILING_DIRECT_ALLOCATION_SAMPLE_LIMIT,
                        PROFILING_DIRECT_ALLOCATION_SAMPLE_LIMIT_DEFAULT);
        profilingExceptionHistogramTopItems =
                configProvider.getInteger(
                        PROFILING_EXCEPTION_HISTOGRAM_TOP_ITEMS,
                        PROFILING_EXCEPTION_HISTOGRAM_TOP_ITEMS_DEFAULT);
        profilingExceptionHistogramMaxCollectionSize =
                configProvider.getInteger(
                        PROFILING_EXCEPTION_HISTOGRAM_MAX_COLLECTION_SIZE,
                        PROFILING_EXCEPTION_HISTOGRAM_MAX_COLLECTION_SIZE_DEFAULT);

        profilingExcludeAgentThreads = configProvider.getBoolean(PROFILING_EXCLUDE_AGENT_THREADS, true);

        profilingRecordExceptionMessage =
                configProvider.getBoolean(
                        PROFILING_EXCEPTION_RECORD_MESSAGE, PROFILING_EXCEPTION_RECORD_MESSAGE_DEFAULT);

        profilingUploadSummaryOn413Enabled =
                configProvider.getBoolean(
                        PROFILING_UPLOAD_SUMMARY_ON_413, PROFILING_UPLOAD_SUMMARY_ON_413_DEFAULT);

        crashTrackingAgentless =
                configProvider.getBoolean(CRASH_TRACKING_AGENTLESS, CRASH_TRACKING_AGENTLESS_DEFAULT);
        crashTrackingTags = configProvider.getMergedMap(CRASH_TRACKING_TAGS);

        float telemetryInterval =
                configProvider.getFloat(TELEMETRY_HEARTBEAT_INTERVAL, DEFAULT_TELEMETRY_HEARTBEAT_INTERVAL);
        if (telemetryInterval < 0.1 || telemetryInterval > 3600) {
            log.warn(
                    "Invalid Telemetry heartbeat interval: {}. The value must be in range 0.1-3600",
                    telemetryInterval);
            telemetryInterval = DEFAULT_TELEMETRY_HEARTBEAT_INTERVAL;
        }
        telemetryHeartbeatInterval = telemetryInterval;

        telemetryExtendedHeartbeatInterval =
                configProvider.getLong(
                        TELEMETRY_EXTENDED_HEARTBEAT_INTERVAL, DEFAULT_TELEMETRY_EXTENDED_HEARTBEAT_INTERVAL);

        telemetryInterval =
                configProvider.getFloat(TELEMETRY_METRICS_INTERVAL, DEFAULT_TELEMETRY_METRICS_INTERVAL);
        if (telemetryInterval < 0.1 || telemetryInterval > 3600) {
            log.warn(
                    "Invalid Telemetry metrics interval: {}. The value must be in range 0.1-3600",
                    telemetryInterval);
            telemetryInterval = DEFAULT_TELEMETRY_METRICS_INTERVAL;
        }
        telemetryMetricsInterval = telemetryInterval;

        telemetryMetricsEnabled =
                configProvider.getBoolean(GeneralConfig.TELEMETRY_METRICS_ENABLED, true);

        isTelemetryDependencyServiceEnabled =
                configProvider.getBoolean(
                        TELEMETRY_DEPENDENCY_COLLECTION_ENABLED,
                        DEFAULT_TELEMETRY_DEPENDENCY_COLLECTION_ENABLED);

        isTelemetryLogCollectionEnabled =
                configProvider.getBoolean(
                        TELEMETRY_LOG_COLLECTION_ENABLED, DEFAULT_TELEMETRY_LOG_COLLECTION_ENABLED);

        clientIpEnabled = configProvider.getBoolean(CLIENT_IP_ENABLED, DEFAULT_CLIENT_IP_ENABLED);

        remoteConfigEnabled =
                configProvider.getBoolean(REMOTE_CONFIG_ENABLED, DEFAULT_REMOTE_CONFIG_ENABLED);
        remoteConfigIntegrityCheckEnabled =
                configProvider.getBoolean(
                        REMOTE_CONFIG_INTEGRITY_CHECK_ENABLED, DEFAULT_REMOTE_CONFIG_INTEGRITY_CHECK_ENABLED);
        remoteConfigUrl = configProvider.getString(REMOTE_CONFIG_URL);
        remoteConfigPollIntervalSeconds =
                configProvider.getFloat(
                        REMOTE_CONFIG_POLL_INTERVAL_SECONDS, DEFAULT_REMOTE_CONFIG_POLL_INTERVAL_SECONDS);
        remoteConfigMaxPayloadSize =
                configProvider.getInteger(
                        REMOTE_CONFIG_MAX_PAYLOAD_SIZE, DEFAULT_REMOTE_CONFIG_MAX_PAYLOAD_SIZE)
                        * 1024;
        remoteConfigTargetsKeyId =
                configProvider.getString(
                        REMOTE_CONFIG_TARGETS_KEY_ID, DEFAULT_REMOTE_CONFIG_TARGETS_KEY_ID);
        remoteConfigTargetsKey =
                configProvider.getString(REMOTE_CONFIG_TARGETS_KEY, DEFAULT_REMOTE_CONFIG_TARGETS_KEY);

        awsPropagationEnabled = isPropagationEnabled(true, "aws", "aws-sdk");
        sqsPropagationEnabled = isPropagationEnabled(true, "sqs");

        kafkaClientPropagationEnabled = isPropagationEnabled(true, "kafka", "kafka.client");
        kafkaClientPropagationDisabledTopics =
                tryMakeImmutableSet(configProvider.getList(KAFKA_CLIENT_PROPAGATION_DISABLED_TOPICS));
        kafkaClientBase64DecodingEnabled =
                configProvider.getBoolean(KAFKA_CLIENT_BASE64_DECODING_ENABLED, false);

        jmsPropagationEnabled = isPropagationEnabled(true, "jms");
        jmsPropagationDisabledTopics =
                tryMakeImmutableSet(configProvider.getList(JMS_PROPAGATION_DISABLED_TOPICS));
        jmsPropagationDisabledQueues =
                tryMakeImmutableSet(configProvider.getList(JMS_PROPAGATION_DISABLED_QUEUES));
        jmsUnacknowledgedMaxAge = configProvider.getInteger(JMS_UNACKNOWLEDGED_MAX_AGE, 3600);

        rabbitPropagationEnabled = isPropagationEnabled(true, "rabbit", "rabbitmq");
        rabbitPropagationDisabledQueues =
                tryMakeImmutableSet(configProvider.getList(RABBIT_PROPAGATION_DISABLED_QUEUES));
        rabbitPropagationDisabledExchanges =
                tryMakeImmutableSet(configProvider.getList(RABBIT_PROPAGATION_DISABLED_EXCHANGES));
        rabbitIncludeRoutingKeyInResource =
                configProvider.getBoolean(RABBIT_INCLUDE_ROUTINGKEY_IN_RESOURCE, true);

        messageBrokerSplitByDestination =
                configProvider.getBoolean(MESSAGE_BROKER_SPLIT_BY_DESTINATION, false);

        grpcIgnoredInboundMethods =
                tryMakeImmutableSet(configProvider.getList(GRPC_IGNORED_INBOUND_METHODS));
        final List<String> tmpGrpcIgnoredOutboundMethods = new ArrayList<>();
        tmpGrpcIgnoredOutboundMethods.addAll(configProvider.getList(GRPC_IGNORED_OUTBOUND_METHODS));
        // When tracing shadowing will be possible we can instrument the stubs to silent tracing
        // starting from interception points
        if (InstrumenterConfig.get()
                .isIntegrationEnabled(Collections.singleton("google-pubsub"), true)) {
            tmpGrpcIgnoredOutboundMethods.addAll(
                    configProvider.getList(
                            GOOGLE_PUBSUB_IGNORED_GRPC_METHODS,
                            Arrays.asList(
                                    "google.pubsub.v1.Subscriber/ModifyAckDeadline",
                                    "google.pubsub.v1.Subscriber/Acknowledge",
                                    "google.pubsub.v1.Subscriber/Pull",
                                    "google.pubsub.v1.Subscriber/StreamingPull",
                                    "google.pubsub.v1.Publisher/Publish")));
        }
        grpcIgnoredOutboundMethods = tryMakeImmutableSet(tmpGrpcIgnoredOutboundMethods);
        grpcServerTrimPackageResource =
                configProvider.getBoolean(GRPC_SERVER_TRIM_PACKAGE_RESOURCE, false);
        grpcServerErrorStatuses =
                configProvider.getIntegerRange(
                        GRPC_SERVER_ERROR_STATUSES, DEFAULT_GRPC_SERVER_ERROR_STATUSES);
        grpcClientErrorStatuses =
                configProvider.getIntegerRange(
                        GRPC_CLIENT_ERROR_STATUSES, DEFAULT_GRPC_CLIENT_ERROR_STATUSES);

        hystrixTagsEnabled = configProvider.getBoolean(HYSTRIX_TAGS_ENABLED, false);
        hystrixMeasuredEnabled = configProvider.getBoolean(HYSTRIX_MEASURED_ENABLED, false);

        igniteCacheIncludeKeys = configProvider.getBoolean(IGNITE_CACHE_INCLUDE_KEYS, false);

        obfuscationQueryRegexp =
                configProvider.getString(
                        OBFUSCATION_QUERY_STRING_REGEXP, null, "obfuscation.query.string.regexp");

        playReportHttpStatus = configProvider.getBoolean(PLAY_REPORT_HTTP_STATUS, false);

        servletPrincipalEnabled = configProvider.getBoolean(SERVLET_PRINCIPAL_ENABLED, false);

        xDatadogTagsMaxLength =
                configProvider.getInteger(
                        TRACE_X_DATADOG_TAGS_MAX_LENGTH, DEFAULT_TRACE_X_DATADOG_TAGS_MAX_LENGTH);

        servletAsyncTimeoutError = configProvider.getBoolean(SERVLET_ASYNC_TIMEOUT_ERROR, true);

        debugEnabled = configProvider.getBoolean(TRACE_DEBUG, false);
        triageEnabled = configProvider.getBoolean(TRACE_TRIAGE, debugEnabled); // debug implies triage

        startupLogsEnabled =
                configProvider.getBoolean(STARTUP_LOGS_ENABLED, DEFAULT_STARTUP_LOGS_ENABLED);

        dataStreamsEnabled =
                configProvider.getBoolean(DATA_STREAMS_ENABLED, DEFAULT_DATA_STREAMS_ENABLED);
        dataStreamsBucketDurationSeconds =
                configProvider.getFloat(
                        DATA_STREAMS_BUCKET_DURATION_SECONDS, DEFAULT_DATA_STREAMS_BUCKET_DURATION);

        azureAppServices = configProvider.getBoolean(AZURE_APP_SERVICES, false);
        traceAgentPath = configProvider.getString(TRACE_AGENT_PATH);
        String traceAgentArgsString = configProvider.getString(TRACE_AGENT_ARGS);
        if (traceAgentArgsString == null) {
            traceAgentArgs = Collections.emptyList();
        } else {
            traceAgentArgs =
                    Collections.unmodifiableList(
                            new ArrayList<>(parseStringIntoSetOfNonEmptyStrings(traceAgentArgsString)));
        }

        dogStatsDPath = configProvider.getString(DOGSTATSD_PATH);
        String dogStatsDArgsString = configProvider.getString(DOGSTATSD_ARGS);
        if (dogStatsDArgsString == null) {
            dogStatsDArgs = Collections.emptyList();
        } else {
            dogStatsDArgs =
                    Collections.unmodifiableList(
                            new ArrayList<>(parseStringIntoSetOfNonEmptyStrings(dogStatsDArgsString)));
        }

        boolean longRunningEnabled =
                configProvider.getBoolean(
                        TracerConfig.TRACE_LONG_RUNNING_ENABLED,
                        DEFAULT_TRACE_LONG_RUNNING_ENABLED);
        long longRunningTraceFlushInterval =
                configProvider.getLong(
                        TracerConfig.TRACE_LONG_RUNNING_FLUSH_INTERVAL,
                        DEFAULT_TRACE_LONG_RUNNING_FLUSH_INTERVAL);

        if (longRunningEnabled
                && (longRunningTraceFlushInterval < 20 || longRunningTraceFlushInterval > 450)) {
            log.warn(
                    "Provided long running trace flush interval of {} seconds. It should be between 20 seconds and 7.5 minutes."
                            + "Setting the flush interval to the default value of {} seconds .",
                    longRunningTraceFlushInterval,
                    DEFAULT_TRACE_LONG_RUNNING_FLUSH_INTERVAL);
            longRunningTraceFlushInterval = DEFAULT_TRACE_LONG_RUNNING_FLUSH_INTERVAL;
        }
        longRunningTraceEnabled = longRunningEnabled;
        this.longRunningTraceFlushInterval = longRunningTraceFlushInterval;

        this.sparkTaskHistogramEnabled =
                configProvider.getBoolean(
                        SPARK_TASK_HISTOGRAM_ENABLED, DEFAULT_SPARK_TASK_HISTOGRAM_ENABLED);

        this.jaxRsExceptionAsErrorsEnabled =
                configProvider.getBoolean(
                        JAX_RS_EXCEPTION_AS_ERROR_ENABLED,
                        DEFAULT_JAX_RS_EXCEPTION_AS_ERROR_ENABLED);

        this.traceFlushIntervalSeconds =
                configProvider.getFloat(
                        TracerConfig.TRACE_FLUSH_INTERVAL, DEFAULT_TRACE_FLUSH_INTERVAL);

        this.telemetryDebugRequestsEnabled =
                configProvider.getBoolean(
                        GeneralConfig.TELEMETRY_DEBUG_REQUESTS_ENABLED,
                        DEFAULT_TELEMETRY_DEBUG_REQUESTS_ENABLED);

        timelineEventsEnabled =
                configProvider.getBoolean(
                        ProfilingConfig.PROFILING_TIMELINE_EVENTS_ENABLED,
                        ProfilingConfig.PROFILING_TIMELINE_EVENTS_ENABLED_DEFAULT);

        log.debug("New instance: {}", this);
    }

    public String getRuntimeId() {
        return runtimeIdEnabled ? RuntimeIdHolder.runtimeId : "";
    }

    public Long getProcessId() {
        return PidHelper.getPidAsLong();
    }

    public String getSite() {
        return site;
    }

    public String getHostName() {
        return HostNameHolder.hostName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isServiceNameSetByUser() {
        return serviceNameSetByUser;
    }

    public String getRootContextServiceName() {
        return rootContextServiceName;
    }

    public boolean isLongRunningTraceEnabled() {
        return longRunningTraceEnabled;
    }

    public long getLongRunningTraceFlushInterval() {
        return longRunningTraceFlushInterval;
    }


    public boolean isInjectBaggageAsTagsEnabled() {
        return injectBaggageAsTagsEnabled;
    }

    public boolean isPrioritySamplingEnabled() {
        return prioritySamplingEnabled;
    }

    public String getPrioritySamplingForce() {
        return prioritySamplingForce;
    }

    public int getSpanAttributeSchemaVersion() {
        return spanAttributeSchemaVersion;
    }

    public boolean isPeerServiceDefaultsEnabled() {
        return peerServiceDefaultsEnabled;
    }

    public Map<String, String> getPeerServiceComponentOverrides() {
        return peerServiceComponentOverrides;
    }

    public boolean isRemoveIntegrationServiceNamesEnabled() {
        return removeIntegrationServiceNamesEnabled;
    }

    public Map<String, String> getPeerServiceMapping() {
        return peerServiceMapping;
    }

    public Map<String, String> getServiceMapping() {
        return serviceMapping;
    }

    public Map<String, String> getRequestHeaderTags() {
        return requestHeaderTags;
    }

    public Map<String, String> getResponseHeaderTags() {
        return responseHeaderTags;
    }

    public boolean isRequestHeaderTagsCommaAllowed() {
        return requestHeaderTagsCommaAllowed;
    }

    public Map<String, String> getBaggageMapping() {
        return baggageMapping;
    }

    public Map<String, String> getHttpServerPathResourceNameMapping() {
        return httpServerPathResourceNameMapping;
    }

    public Map<String, String> getHttpClientPathResourceNameMapping() {
        return httpClientPathResourceNameMapping;
    }

    public boolean getHttpResourceRemoveTrailingSlash() {
        return httpResourceRemoveTrailingSlash;
    }

    public boolean isHttpServerDecodedResourcePreserveSpaces() {
        return httpServerDecodedResourcePreserveSpaces;
    }

    public Set<String> getSplitByTags() {
        return splitByTags;
    }

    public int getScopeDepthLimit() {
        return scopeDepthLimit;
    }

    public boolean isScopeStrictMode() {
        return scopeStrictMode;
    }

    public boolean isScopeInheritAsyncPropagation() {
        return scopeInheritAsyncPropagation;
    }

    public int getScopeIterationKeepAlive() {
        return scopeIterationKeepAlive;
    }

    public int getPartialFlushMinSpans() {
        return partialFlushMinSpans;
    }

    public boolean isTraceStrictWritesEnabled() {
        return traceStrictWritesEnabled;
    }

    public boolean isLogExtractHeaderNames() {
        return logExtractHeaderNames;
    }

    public boolean isTracePropagationStyleB3PaddingEnabled() {
        return tracePropagationStyleB3PaddingEnabled;
    }

    public Set<TracePropagationStyle> getTracePropagationStylesToExtract() {
        return tracePropagationStylesToExtract;
    }

    public Set<TracePropagationStyle> getTracePropagationStylesToInject() {
        return tracePropagationStylesToInject;
    }

    public boolean isTracePropagationExtractFirst() {
        return tracePropagationExtractFirst;
    }

    public int getClockSyncPeriod() {
        return clockSyncPeriod;
    }

    public boolean isRuntimeMetricsEnabled() {
        return runtimeMetricsEnabled;
    }

    public boolean isHealthMetricsEnabled() {
        return healthMetricsEnabled;
    }

    public boolean isLogsInjectionEnabled() {
        return logsInjectionEnabled;
    }

    public String getTraceClientIpHeader() {
        return traceClientIpHeader;
    }

    // whether to collect headers and run the client ip resolution (also requires appsec to be enabled
    // or clientIpEnabled)
    public boolean isTraceClientIpResolverEnabled() {
        return traceClientIpResolverEnabled;
    }

    public Map<String, String> getTraceSamplingServiceRules() {
        return traceSamplingServiceRules;
    }

    public Map<String, String> getTraceSamplingOperationRules() {
        return traceSamplingOperationRules;
    }

    public String getTraceSamplingRules() {
        return traceSamplingRules;
    }

    public Double getTraceSampleRate() {
        return traceSampleRate;
    }

    public int getTraceRateLimit() {
        return traceRateLimit;
    }

    public String getSpanSamplingRules() {
        return spanSamplingRules;
    }

    public String getSpanSamplingRulesFile() {
        return spanSamplingRulesFile;
    }

    public boolean isProfilingEnabled() {
        return profilingEnabled && instrumenterConfig.isProfilingEnabled();
    }

    public static boolean isDatadogProfilerEnablementOverridden() {
        // old non-LTS versions without important backports
        // also, we have no windows binaries
        return Platform.isJavaVersion(18)
                || Platform.isJavaVersion(16)
                || Platform.isJavaVersion(15)
                || Platform.isJavaVersion(14)
                || Platform.isJavaVersion(13)
                || Platform.isJavaVersion(12)
                || Platform.isJavaVersion(10)
                || Platform.isJavaVersion(9);
    }

    public static boolean isDatadogProfilerSafeInCurrentEnvironment() {
        // don't want to put this logic (which will evolve) in the public ProfilingConfig, and can't
        // access Platform there
        if (!Platform.isJ9() && Platform.isJavaVersion(8)) {
            String arch = System.getProperty("os.arch");
            if ("aarch64".equalsIgnoreCase(arch) || "arm64".equalsIgnoreCase(arch)) {
                return false;
            }
        }
        if (Platform.isGraalVM()) {
            // let's be conservative about GraalVM and require opt-in from the users
            return false;
        }
        boolean result =
                Platform.isJ9()
                        || !Platform.isJavaVersion(18) // missing AGCT fixes
                        || Platform.isJavaVersionAtLeast(17, 0, 5)
                        || (Platform.isJavaVersion(11) && Platform.isJavaVersionAtLeast(11, 0, 17))
                        || (Platform.isJavaVersion(8) && Platform.isJavaVersionAtLeast(8, 0, 352));

        if (result && Platform.isJ9()) {
            // Semeru JDK 11 and JDK 17 have problems with unloaded classes and jmethodids, leading to JVM
            // crash
            // The ASGCT based profilers are only activated in JDK 11.0.18+ and JDK 17.0.6+
            result &=
                    !((Platform.isJavaVersion(11) && Platform.isJavaVersionAtLeast(11, 0, 18))
                            || ((Platform.isJavaVersion(17) && Platform.isJavaVersionAtLeast(17, 0, 6))));
        }
        return result;
    }

    public boolean isClientIpEnabled() {
        return clientIpEnabled;
    }

    public boolean isCiVisibilityEnabled() {
        return false;
    }

    public boolean isAwsPropagationEnabled() {
        return awsPropagationEnabled;
    }

    public String getObfuscationQueryRegexp() {
        return obfuscationQueryRegexp;
    }

    public int getxDatadogTagsMaxLength() {
        return xDatadogTagsMaxLength;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isDataStreamsEnabled() {
        return dataStreamsEnabled;
    }

    public IdGenerationStrategy getIdGenerationStrategy() {
        return idGenerationStrategy;
    }

    /**
     * @return A map of tags to be applied only to the local application root span.
     */
    public Map<String, Object> getLocalRootSpanTags() {
        final Map<String, String> runtimeTags = getRuntimeTags();
        final Map<String, Object> result = new HashMap<>(runtimeTags.size() + 2);
        result.putAll(runtimeTags);
        result.put(LANGUAGE_TAG_KEY, LANGUAGE_TAG_VALUE);
        result.put(SCHEMA_VERSION_TAG_KEY, SpanNaming.instance().version());
        result.put(PROFILING_ENABLED, isProfilingEnabled() ? 1 : 0);

        if (reportHostName) {
            final String hostName = getHostName();
            if (null != hostName && !hostName.isEmpty()) {
                result.put(INTERNAL_HOST_NAME, hostName);
            }
        }

        if (azureAppServices) {
            result.putAll(getAzureAppServicesTags());
        }

        result.putAll(getProcessIdTag());

        return Collections.unmodifiableMap(result);
    }

    public String getPrimaryTag() {
        return primaryTag;
    }

    public String getEnv() {
        // intentionally not thread safe
        if (env == null) {
            env = getMergedSpanTags().get("env");
            if (env == null) {
                env = "";
            }
        }

        return env;
    }

    public String getVersion() {
        // intentionally not thread safe
        if (version == null) {
            version = getMergedSpanTags().get("version");
            if (version == null) {
                version = "";
            }
        }

        return version;
    }

    public Map<String, String> getMergedSpanTags() {
        // Do not include runtimeId into span tags: we only want that added to the root span
        final Map<String, String> result = newHashMap(getGlobalTags().size() + spanTags.size());
        result.putAll(getGlobalTags());
        result.putAll(spanTags);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Provide 'global' tags, i.e. tags set everywhere. We have to support old (dd.trace.global.tags)
     * version of this setting if new (dd.tags) version has not been specified.
     */
    public Map<String, String> getGlobalTags() {
        return tags;
    }

    /**
     * Return a map of tags required by the datadog backend to link runtime metrics (i.e. jmx) and
     * traces.
     *
     * <p>These tags must be applied to every runtime metrics and placed on the root span of every
     * trace.
     *
     * @return A map of tag-name -> tag-value
     */
    private Map<String, String> getRuntimeTags() {
        return Collections.singletonMap(RUNTIME_ID_TAG, getRuntimeId());
    }

    private Map<String, Long> getProcessIdTag() {
        return Collections.singletonMap(PID_TAG, getProcessId());
    }

    private Map<String, String> getAzureAppServicesTags() {
        // These variable names and derivations are copied from the dotnet tracer
        // See
        // https://github.com/DataDog/dd-trace-dotnet/blob/master/tracer/src/com.datadog.trace/PlatformHelpers/AzureAppServices.cs
        // and
        // https://github.com/DataDog/dd-trace-dotnet/blob/master/tracer/src/com.datadog.trace/TraceContext.cs#L207
        Map<String, String> aasTags = new HashMap<>();

        /// The site name of the site instance in Azure where the traced application is running.
        String siteName = getEnv("WEBSITE_SITE_NAME");
        if (siteName != null) {
            aasTags.put("aas.site.name", siteName);
        }

        // The kind of application instance running in Azure.
        // Possible values: app, api, mobileapp, app_linux, app_linux_container, functionapp,
        // functionapp_linux, functionapp_linux_container

        // The type of application instance running in Azure.
        // Possible values: app, function
        if (getEnv("FUNCTIONS_WORKER_RUNTIME") != null
                || getEnv("FUNCTIONS_EXTENSIONS_VERSION") != null) {
            aasTags.put("aas.site.kind", "functionapp");
            aasTags.put("aas.site.type", "function");
        } else {
            aasTags.put("aas.site.kind", "app");
            aasTags.put("aas.site.type", "app");
        }

        //  The resource group of the site instance in Azure App Services
        String resourceGroup = getEnv("WEBSITE_RESOURCE_GROUP");
        if (resourceGroup != null) {
            aasTags.put("aas.resource.group", resourceGroup);
        }

        // Example: 8c500027-5f00-400e-8f00-60000000000f+apm-dotnet-EastUSwebspace
        // Format: {subscriptionId}+{planResourceGroup}-{hostedInRegion}
        String websiteOwner = getEnv("WEBSITE_OWNER_NAME");
        int plusIndex = websiteOwner == null ? -1 : websiteOwner.indexOf("+");

        // The subscription ID of the site instance in Azure App Services
        String subscriptionId = null;
        if (plusIndex > 0) {
            subscriptionId = websiteOwner.substring(0, plusIndex);
            aasTags.put("aas.subscription.id", subscriptionId);
        }

        if (subscriptionId != null && siteName != null && resourceGroup != null) {
            // The resource ID of the site instance in Azure App Services
            String resourceId =
                    "/subscriptions/"
                            + subscriptionId
                            + "/resourcegroups/"
                            + resourceGroup
                            + "/providers/microsoft.web/sites/"
                            + siteName;
            resourceId = resourceId.toLowerCase(Locale.ROOT);
            aasTags.put("aas.resource.id", resourceId);
        } else {
            log.warn(
                    "Unable to generate resource id subscription id: {}, site name: {}, resource group {}",
                    subscriptionId,
                    siteName,
                    resourceGroup);
        }

        // The instance ID in Azure
        String instanceId = getEnv("WEBSITE_INSTANCE_ID");
        instanceId = instanceId == null ? "unknown" : instanceId;
        aasTags.put("aas.environment.instance_id", instanceId);

        // The instance name in Azure
        String instanceName = getEnv("COMPUTERNAME");
        instanceName = instanceName == null ? "unknown" : instanceName;
        aasTags.put("aas.environment.instance_name", instanceName);

        // The operating system in Azure
        String operatingSystem = getEnv("WEBSITE_OS");
        operatingSystem = operatingSystem == null ? "unknown" : operatingSystem;
        aasTags.put("aas.environment.os", operatingSystem);

        // The version of the extension installed
        String siteExtensionVersion = getEnv("DD_AAS_JAVA_EXTENSION_VERSION");
        siteExtensionVersion = siteExtensionVersion == null ? "unknown" : siteExtensionVersion;
        aasTags.put("aas.environment.extension_version", siteExtensionVersion);

        aasTags.put("aas.environment.runtime", getProp("java.vm.name", "unknown"));

        return aasTags;
    }

    private int schemaVersionFromConfig() {
        String versionStr =
                configProvider.getString(TRACE_SPAN_ATTRIBUTE_SCHEMA, "v" + SpanNaming.SCHEMA_MIN_VERSION);
        Matcher matcher = Pattern.compile("^v?(0|[1-9]\\d*)$").matcher(versionStr);
        int parsedVersion = -1;
        if (matcher.matches()) {
            parsedVersion = Integer.parseInt(matcher.group(1));
        }
        if (parsedVersion < SpanNaming.SCHEMA_MIN_VERSION
                || parsedVersion > SpanNaming.SCHEMA_MAX_VERSION) {
            log.warn(
                    "Invalid attribute schema version {} invalid or out of range [v{}, v{}]. Defaulting to v{}",
                    versionStr,
                    SpanNaming.SCHEMA_MIN_VERSION,
                    SpanNaming.SCHEMA_MAX_VERSION,
                    SpanNaming.SCHEMA_MIN_VERSION);
            parsedVersion = SpanNaming.SCHEMA_MIN_VERSION;
        }
        return parsedVersion;
    }

    public boolean isJmxFetchIntegrationEnabled(
            final Iterable<String> integrationNames, final boolean defaultEnabled) {
        return configProvider.isEnabled(integrationNames, "jmxfetch.", ".enabled", defaultEnabled);
    }

    public boolean isRuleEnabled(final String name, boolean defaultEnabled) {
        boolean enabled = configProvider.getBoolean("trace." + name + ".enabled", defaultEnabled);
        boolean lowerEnabled =
                configProvider.getBoolean(
                        "trace." + name.toLowerCase(Locale.ROOT) + ".enabled", defaultEnabled);
        return defaultEnabled ? enabled && lowerEnabled : enabled || lowerEnabled;
    }

    /**
     * @param integrationNames
     * @param defaultEnabled
     * @return
     * @deprecated This method should only be used internally. Use the instance getter instead {@link
     * #isJmxFetchIntegrationEnabled(Iterable, boolean)}.
     */
    @Deprecated
    public static boolean jmxFetchIntegrationEnabled(
            final SortedSet<String> integrationNames, final boolean defaultEnabled) {
        return Config.get().isJmxFetchIntegrationEnabled(integrationNames, defaultEnabled);
    }

    public boolean isEndToEndDurationEnabled(
            final boolean defaultEnabled, final String... integrationNames) {
        return configProvider.isEnabled(
                Arrays.asList(integrationNames), "", ".e2e.duration.enabled", defaultEnabled);
    }

    public boolean isPropagationEnabled(
            final boolean defaultEnabled, final String... integrationNames) {
        return configProvider.isEnabled(
                Arrays.asList(integrationNames), "", ".propagation.enabled", defaultEnabled);
    }

    public boolean isEnabled(
            final boolean defaultEnabled, final String settingName, String settingSuffix) {
        return configProvider.isEnabled(
                Collections.singletonList(settingName), "", settingSuffix, defaultEnabled);
    }

    private void logIgnoredSettingWarning(
            String setting, String overridingSetting, String overridingSuffix) {
        log.warn(
                "Setting {} ignored since {}{} is enabled.",
                propertyNameToSystemPropertyName(setting),
                propertyNameToSystemPropertyName(overridingSetting),
                overridingSuffix);
    }

    private void logOverriddenSettingWarning(String setting, String overridingSetting, Object value) {
        log.warn(
                "Setting {} is overridden by setting {} with value {}.",
                propertyNameToSystemPropertyName(setting),
                propertyNameToSystemPropertyName(overridingSetting),
                value);
    }

    private void logOverriddenDeprecatedSettingWarning(
            String setting, String overridingSetting, Object value) {
        log.warn(
                "Setting {} is deprecated and overridden by setting {} with value {}.",
                propertyNameToSystemPropertyName(setting),
                propertyNameToSystemPropertyName(overridingSetting),
                value);
    }

    private void logDeprecatedConvertedSetting(
            String deprecatedSetting, Object oldValue, String newSetting, Object newValue) {
        log.warn(
                "Setting {} is deprecated and the value {} has been converted to {} for setting {}.",
                propertyNameToSystemPropertyName(deprecatedSetting),
                oldValue,
                newValue,
                propertyNameToSystemPropertyName(newSetting));
    }

    public boolean isTraceAnalyticsIntegrationEnabled(
            final SortedSet<String> integrationNames, final boolean defaultEnabled) {
        return configProvider.isEnabled(integrationNames, "", ".analytics.enabled", defaultEnabled);
    }

    public boolean isSamplingMechanismValidationDisabled() {
        return configProvider.getBoolean(TracerConfig.SAMPLING_MECHANISM_VALIDATION_DISABLED, false);
    }

    /**
     * @param integrationNames
     * @param defaultEnabled
     * @return
     * @deprecated This method should only be used internally. Use the instance getter instead {@link
     * #isTraceAnalyticsIntegrationEnabled(SortedSet, boolean)}.
     */
    @Deprecated
    public static boolean traceAnalyticsIntegrationEnabled(
            final SortedSet<String> integrationNames, final boolean defaultEnabled) {
        return Config.get().isTraceAnalyticsIntegrationEnabled(integrationNames, defaultEnabled);
    }

    private <T> Set<T> getSettingsSetFromEnvironment(
            String name, Function<String, T> mapper, boolean splitOnWS) {
        final String value = configProvider.getString(name, "");
        return convertStringSetToSet(
                name, parseStringIntoSetOfNonEmptyStrings(value, splitOnWS), mapper);
    }

    private <F, T> Set<T> convertSettingsSet(Set<F> fromSet, Function<F, Iterable<T>> mapper) {
        if (fromSet.isEmpty()) {
            return Collections.emptySet();
        }
        Set<T> result = new LinkedHashSet<>(fromSet.size());
        for (F from : fromSet) {
            for (T to : mapper.apply(from)) {
                result.add(to);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static final String PREFIX = "dd.";

    /**
     * Converts the property name, e.g. 'service.name' into a public system property name, e.g.
     * `dd.service.name`.
     *
     * @param setting The setting name, e.g. `service.name`
     * @return The public facing system property name
     */
    @NonNull
    private static String propertyNameToSystemPropertyName(final String setting) {
        return PREFIX + setting;
    }

    @NonNull
    private static Map<String, String> newHashMap(final int size) {
        return new HashMap<>(size + 1, 1f);
    }

    /**
     * @param map
     * @param propNames
     * @return new unmodifiable copy of {@param map} where properties are overwritten from environment
     */
    @NonNull
    private Map<String, String> getMapWithPropertiesDefinedByEnvironment(
            @NonNull final Map<String, String> map, @NonNull final String... propNames) {
        final Map<String, String> res = new HashMap<>(map);
        for (final String propName : propNames) {
            final String val = configProvider.getString(propName);
            if (val != null) {
                res.put(propName, val);
            }
        }
        return Collections.unmodifiableMap(res);
    }

    @NonNull
    private static Set<String> parseStringIntoSetOfNonEmptyStrings(final String str) {
        return parseStringIntoSetOfNonEmptyStrings(str, true);
    }

    @NonNull
    private static Set<String> parseStringIntoSetOfNonEmptyStrings(
            final String str, boolean splitOnWS) {
        // Using LinkedHashSet to preserve original string order
        final Set<String> result = new LinkedHashSet<>();
        // Java returns single value when splitting an empty string. We do not need that value, so
        // we need to throw it out.
        int start = 0;
        int i = 0;
        for (; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (c == ',' || (splitOnWS && Character.isWhitespace(c))) {
                if (i - start - 1 > 0) {
                    result.add(str.substring(start, i));
                }
                start = i + 1;
            }
        }
        if (i - start - 1 > 0) {
            result.add(str.substring(start));
        }
        return Collections.unmodifiableSet(result);
    }

    private static <T> Set<T> convertStringSetToSet(
            String setting, final Set<String> input, Function<String, T> mapper) {
        if (input.isEmpty()) {
            return Collections.emptySet();
        }
        // Using LinkedHashSet to preserve original string order
        final Set<T> result = new LinkedHashSet<>();
        for (final String value : input) {
            try {
                result.add(mapper.apply(value));
            } catch (final IllegalArgumentException e) {
                log.warn(
                        "Cannot recognize config string value {} for setting {}",
                        value,
                        propertyNameToSystemPropertyName(setting));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the detected hostname. First tries locally, then using DNS
     */
    static String initHostName() {
        String possibleHostname = getEnv("HOSTNAME");

        if (possibleHostname != null && !possibleHostname.isEmpty()) {
            log.debug("Determined hostname from environment variable");
            return possibleHostname.trim();
        }

        // Try hostname command
        try (final BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(Runtime.getRuntime().exec("hostname").getInputStream()))) {
            possibleHostname = reader.readLine();
        } catch (final Throwable ignore) {
            // Ignore.  Hostname command is not always available
        }

        if (possibleHostname != null && !possibleHostname.isEmpty()) {
            log.debug("Determined hostname from hostname command");
            return possibleHostname.trim();
        }

        // From DNS
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            // If we are not able to detect the hostname we do not throw an exception.
        }

        return null;
    }

    private static String getEnv(String name) {
        String value = System.getenv(name);
        if (value != null) {
            ConfigCollector.get().put(name, value, ConfigOrigin.ENV);
        }
        return value;
    }

    private static String getProp(String name) {
        return getProp(name, null);
    }

    private static String getProp(String name, String def) {
        String value = System.getProperty(name, def);
        if (value != null) {
            ConfigCollector.get().put(name, value, ConfigOrigin.JVM_PROP);
        }
        return value;
    }

    // This has to be placed after all other static fields to give them a chance to initialize

    private static final Config INSTANCE =
            new Config(
                    ConfigProvider.getInstance(),
                    InstrumenterConfig.get());

    public static Config get() {
        return INSTANCE;
    }

    /**
     * This method is deprecated since the method of configuration will be changed in the future. The
     * properties instance should instead be passed directly into the DDTracer builder:
     *
     * <pre>
     *   DDTracer.builder().withProperties(new Properties()).build()
     * </pre>
     *
     * <p>Config keys for use in Properties instance construction can be found in {@link
     * GeneralConfig} and {@link TracerConfig}.
     *
     * @deprecated
     */
    @Deprecated
    public static Config get(final Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return INSTANCE;
        } else {
            return new Config(ConfigProvider.withPropertiesOverride(properties));
        }
    }

    @Override
    public String toString() {
        return "Config{"
                + "instrumenterConfig="
                + instrumenterConfig
                + ", runtimeId='"
                + getRuntimeId()
                + '\''
                + ", runtimeVersion='"
                + runtimeVersion
                + ", site='"
                + site
                + '\''
                + ", hostName='"
                + getHostName()
                + '\''
                + ", serviceName='"
                + serviceName
                + '\''
                + ", serviceNameSetByUser="
                + serviceNameSetByUser
                + ", rootContextServiceName="
                + rootContextServiceName
                + ", integrationSynapseLegacyOperationName="
                + integrationSynapseLegacyOperationName
                + ", writerType='"
                + writerType
                + '\''
                + ", agentConfiguredUsingDefault="
                + agentConfiguredUsingDefault
                + ", agentUrl='"
                + agentUrl
                + '\''
                + ", agentHost='"
                + agentHost
                + '\''
                + ", agentPort="
                + agentPort
                + '\''
                + ", agentTimeout="
                + agentTimeout
                + ", noProxyHosts="
                + noProxyHosts
                + ", prioritySamplingEnabled="
                + prioritySamplingEnabled
                + ", prioritySamplingForce='"
                + prioritySamplingForce
                + '\''
                + ", traceResolverEnabled="
                + traceResolverEnabled
                + ", serviceMapping="
                + serviceMapping
                + ", tags="
                + tags
                + ", spanTags="
                + spanTags
                + ", requestHeaderTags="
                + requestHeaderTags
                + ", responseHeaderTags="
                + responseHeaderTags
                + ", baggageMapping="
                + baggageMapping
                + ", httpServerErrorStatuses="
                + httpServerErrorStatuses
                + ", httpClientErrorStatuses="
                + httpClientErrorStatuses
                + ", httpServerTagQueryString="
                + httpServerTagQueryString
                + ", httpServerRawQueryString="
                + httpServerRawQueryString
                + ", httpServerRawResource="
                + httpServerRawResource
                + ", httpServerRouteBasedNaming="
                + httpServerRouteBasedNaming
                + ", httpServerPathResourceNameMapping="
                + httpServerPathResourceNameMapping
                + ", httpClientPathResourceNameMapping="
                + httpClientPathResourceNameMapping
                + ", httpClientTagQueryString="
                + httpClientTagQueryString
                + ", httpClientSplitByDomain="
                + httpClientSplitByDomain
                + ", httpResourceRemoveTrailingSlash"
                + httpResourceRemoveTrailingSlash
                + ", dbClientSplitByInstance="
                + dbClientSplitByInstance
                + ", dbClientSplitByInstanceTypeSuffix="
                + dbClientSplitByInstanceTypeSuffix
                + ", dbClientSplitByHost="
                + dbClientSplitByHost
                + ", DBMPropagationMode="
                + DBMPropagationMode
                + ", splitByTags="
                + splitByTags
                + ", scopeDepthLimit="
                + scopeDepthLimit
                + ", scopeStrictMode="
                + scopeStrictMode
                + ", scopeInheritAsyncPropagation="
                + scopeInheritAsyncPropagation
                + ", scopeIterationKeepAlive="
                + scopeIterationKeepAlive
                + ", partialFlushMinSpans="
                + partialFlushMinSpans
                + ", traceStrictWritesEnabled="
                + traceStrictWritesEnabled
                + ", tracePropagationStylesToExtract="
                + tracePropagationStylesToExtract
                + ", tracePropagationStylesToInject="
                + tracePropagationStylesToInject
                + ", tracePropagationExtractFirst="
                + tracePropagationExtractFirst
                + ", clockSyncPeriod="
                + clockSyncPeriod
                + ", healthMetricsEnabled="
                + healthMetricsEnabled
                + ", healthMetricsStatsdHost='"
                + healthMetricsStatsdHost
                + '\''
                + ", healthMetricsStatsdPort="
                + healthMetricsStatsdPort
                + ", perfMetricsEnabled="
                + perfMetricsEnabled
                + ", tracerMetricsEnabled="
                + tracerMetricsEnabled
                + ", tracerMetricsBufferingEnabled="
                + tracerMetricsBufferingEnabled
                + ", tracerMetricsMaxAggregates="
                + tracerMetricsMaxAggregates
                + ", tracerMetricsMaxPending="
                + tracerMetricsMaxPending
                + ", reportHostName="
                + reportHostName
                + ", traceAnalyticsEnabled="
                + traceAnalyticsEnabled
                + ", traceSamplingServiceRules="
                + traceSamplingServiceRules
                + ", traceSamplingOperationRules="
                + traceSamplingOperationRules
                + ", traceSamplingJsonRules="
                + traceSamplingRules
                + ", traceSampleRate="
                + traceSampleRate
                + ", traceRateLimit="
                + traceRateLimit
                + ", spanSamplingRules="
                + spanSamplingRules
                + ", spanSamplingRulesFile="
                + spanSamplingRulesFile
                + ", profilingAgentless="
                + profilingAgentless
                + ", profilingUrl='"
                + profilingUrl
                + '\''
                + ", profilingTags="
                + profilingTags
                + ", profilingStartDelay="
                + profilingStartDelay
                + ", profilingStartForceFirst="
                + profilingStartForceFirst
                + ", profilingUploadPeriod="
                + profilingUploadPeriod
                + ", profilingTemplateOverrideFile='"
                + profilingTemplateOverrideFile
                + '\''
                + ", profilingUploadTimeout="
                + profilingUploadTimeout
                + ", profilingUploadCompression='"
                + profilingUploadCompression
                + '\''
                + ", profilingProxyHost='"
                + profilingProxyHost
                + '\''
                + ", profilingProxyPort="
                + profilingProxyPort
                + ", profilingProxyUsername='"
                + profilingProxyUsername
                + '\''
                + ", profilingProxyPassword="
                + (profilingProxyPassword == null ? "null" : "****")
                + ", profilingExceptionSampleLimit="
                + profilingExceptionSampleLimit
                + ", profilingExceptionHistogramTopItems="
                + profilingExceptionHistogramTopItems
                + ", profilingExceptionHistogramMaxCollectionSize="
                + profilingExceptionHistogramMaxCollectionSize
                + ", profilingExcludeAgentThreads="
                + profilingExcludeAgentThreads
                + ", crashTrackingTags="
                + crashTrackingTags
                + ", crashTrackingAgentless="
                + crashTrackingAgentless
                + ", remoteConfigEnabled="
                + remoteConfigEnabled
                + ", remoteConfigUrl="
                + remoteConfigUrl
                + ", remoteConfigPollIntervalSeconds="
                + remoteConfigPollIntervalSeconds
                + ", remoteConfigMaxPayloadSize="
                + remoteConfigMaxPayloadSize
                + ", remoteConfigIntegrityCheckEnabled="
                + remoteConfigIntegrityCheckEnabled
                + ", awsPropagationEnabled="
                + awsPropagationEnabled
                + ", sqsPropagationEnabled="
                + sqsPropagationEnabled
                + ", kafkaClientPropagationEnabled="
                + kafkaClientPropagationEnabled
                + ", kafkaClientPropagationDisabledTopics="
                + kafkaClientPropagationDisabledTopics
                + ", kafkaClientBase64DecodingEnabled="
                + kafkaClientBase64DecodingEnabled
                + ", jmsPropagationEnabled="
                + jmsPropagationEnabled
                + ", jmsPropagationDisabledTopics="
                + jmsPropagationDisabledTopics
                + ", jmsPropagationDisabledQueues="
                + jmsPropagationDisabledQueues
                + ", rabbitPropagationEnabled="
                + rabbitPropagationEnabled
                + ", rabbitPropagationDisabledQueues="
                + rabbitPropagationDisabledQueues
                + ", rabbitPropagationDisabledExchanges="
                + rabbitPropagationDisabledExchanges
                + ", messageBrokerSplitByDestination="
                + messageBrokerSplitByDestination
                + ", hystrixTagsEnabled="
                + hystrixTagsEnabled
                + ", hystrixMeasuredEnabled="
                + hystrixMeasuredEnabled
                + ", igniteCacheIncludeKeys="
                + igniteCacheIncludeKeys
                + ", servletPrincipalEnabled="
                + servletPrincipalEnabled
                + ", servletAsyncTimeoutError="
                + servletAsyncTimeoutError
                + ", datadogTagsLimit="
                + xDatadogTagsMaxLength
                + ", traceAgentV05Enabled="
                + traceAgentV05Enabled
                + ", debugEnabled="
                + debugEnabled
                + ", triageEnabled="
                + triageEnabled
                + ", startLogsEnabled="
                + startupLogsEnabled
                + ", configFile='"
                + configFileStatus
                + '\''
                + ", idGenerationStrategy="
                + idGenerationStrategy
                + ", trace128bitTraceIdGenerationEnabled="
                + trace128bitTraceIdGenerationEnabled
                + ", grpcIgnoredInboundMethods="
                + grpcIgnoredInboundMethods
                + ", grpcIgnoredOutboundMethods="
                + grpcIgnoredOutboundMethods
                + ", grpcServerErrorStatuses="
                + grpcServerErrorStatuses
                + ", grpcClientErrorStatuses="
                + grpcClientErrorStatuses
                + ", clientIpEnabled="
                + clientIpEnabled
                + ", longRunningTraceEnabled="
                + longRunningTraceEnabled
                + ", longRunningTraceFlushInterval="
                + longRunningTraceFlushInterval
                + ", elasticsearchBodyEnabled="
                + elasticsearchBodyEnabled
                + ", elasticsearchParamsEnabled="
                + elasticsearchParamsEnabled
                + ", elasticsearchBodyAndParamsEnabled="
                + elasticsearchBodyAndParamsEnabled
                + ", traceFlushInterval="
                + traceFlushIntervalSeconds
                + ", injectBaggageAsTagsEnabled="
                + injectBaggageAsTagsEnabled
                + ", logsInjectionEnabled="
                + logsInjectionEnabled
                + ", sparkTaskHistogramEnabled="
                + sparkTaskHistogramEnabled
                + ", jaxRsExceptionAsErrorsEnabled="
                + jaxRsExceptionAsErrorsEnabled
                + ", peerServiceDefaultsEnabled="
                + peerServiceDefaultsEnabled
                + ", peerServiceComponentOverrides="
                + peerServiceComponentOverrides
                + ", removeIntegrationServiceNamesEnabled="
                + removeIntegrationServiceNamesEnabled
                + ", spanAttributeSchemaVersion="
                + spanAttributeSchemaVersion
                + ", telemetryDebugRequestsEnabled="
                + telemetryDebugRequestsEnabled
                + ", telemetryMetricsEnabled="
                + telemetryMetricsEnabled
                + '}';
    }
}
