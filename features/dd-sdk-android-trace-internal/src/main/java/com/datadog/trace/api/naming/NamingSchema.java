package com.datadog.trace.api.naming;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

public interface NamingSchema {
  /**
   * Get the naming policy for caches.
   *
   * @return a {@link ForCache} instance.
   */
  ForCache cache();

  /**
   * Get the naming policy for clients (http, soap, ...).
   *
   * @return a {@link ForClient} instance.
   */
  ForClient client();

  /**
   * Get the naming policy for cloud providers (aws, gpc, azure, ...).
   *
   * @return a {@link ForCloud} instance.
   */
  ForCloud cloud();

  /**
   * Get the naming policy for databases.
   *
   * @return a {@link ForDatabase} instance.
   */
  ForDatabase database();

  /**
   * Get the naming policy for messaging.
   *
   * @return a {@link ForMessaging} instance.
   */
  ForMessaging messaging();

  /**
   * Get the naming policy for servers.
   *
   * @return a {@link ForServer} instance.
   */
  ForServer server();

  /**
   * Policy for peer service tags calculation
   *
   * @return
   */
  ForPeerService peerService();

  /**
   * If true, the schema allows having service names != DD_SERVICE
   *
   * @return
   */
  boolean allowInferredServices();

  interface ForCache {
    /**
     * Calculate the operation name for a cache span.
     *
     * @param cacheSystem the caching system (e.g. redis, memcached,..)
     * @return the operation name
     */
    @NonNull
    String operation(@NonNull String cacheSystem);

    /**
     * Calculate the service name for a cache span.
     *
     * @param cacheSystem the caching system (e.g. redis, memcached,..)
     * @return the service name
     */
    String service(@NonNull String cacheSystem);
  }

  interface ForClient {
    /**
     * Calculate the operation name for a client span.
     *
     * @param protocol the protocol used (e.g. http, ftp, ..)
     * @return the operation name
     */
    @NonNull
    String operationForProtocol(@NonNull String protocol);

    /**
     * Calculate the operation name for a client span.
     *
     * @param component the name of the instrumentation componen
     * @return the operation name
     */
    @NonNull
    String operationForComponent(@NonNull String component);
  }

  interface ForCloud {

    /**
     * Calculate the operation name for a generic cloud sdk call.
     *
     * @param provider the cloud provider
     * @param cloudService the cloud service name (e.g. s3)
     * @param serviceOperation the qualified service operation (e.g.S3.CreateBucket)
     * @return the operation name for this span
     */
    @NonNull
    String operationForRequest(
        @NonNull String provider, @NonNull String cloudService, @NonNull String serviceOperation);

    /**
     * Calculate the service name for a generic cloud sdk call.
     *
     * @param provider the cloud provider
     * @param cloudService the cloud service name (e.g. s3). If not provided the method should
     *     return a default value
     * @return the service name for this span
     */
    String serviceForRequest(@NonNull String provider, @Nullable String cloudService);

    /**
     * Calculate the operation name for a function as a service invocation (e.g. aws lambda)
     *
     * @param provider the cloud provider
     * @return the operation name for this span
     */
    @NonNull
    String operationForFaas(@NonNull String provider);
  }

  interface ForDatabase {
    /**
     * Normalize the cache name from the raw parsed one.
     *
     * @param rawName the raw name
     * @return the normalized one
     */
    String normalizedName(@NonNull String rawName);

    /**
     * Calculate the operation name for a database span.
     *
     * @param databaseType the database type (e.g. postgres, elasticsearch,..)
     * @return the operation name
     */
    @NonNull
    String operation(@NonNull String databaseType);

    /**
     * Calculate the service name for a database span.
     *
     * @param databaseType the database type (e.g. postgres, elasticsearch,..)
     * @return the service name
     */
    String service(@NonNull String databaseType);
  }

  interface ForMessaging {
    /**
     * Calculate the operation name for a messaging consumer span for process operation.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka,..)
     * @return the operation name
     */
    @NonNull
    String inboundOperation(@NonNull String messagingSystem);

    /**
     * Calculate the service name for a messaging producer span.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka, amqp,..)
     * @param useLegacyTracing if true legacy tracing service naming will be applied if compatible
     * @return the service name
     */
    String inboundService(@NonNull String messagingSystem, boolean useLegacyTracing);

    /**
     * Calculate the operation name for a messaging producer span.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka, amqp,..)
     * @return the operation name
     */
    @NonNull
    String outboundOperation(@NonNull String messagingSystem);

    /**
     * Calculate the service name for a messaging producer span.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka, amqp,..)
     * @param useLegacyTracing if true legacy tracing service naming will be applied if compatible
     * @return the service name
     */
    String outboundService(@NonNull String messagingSystem, boolean useLegacyTracing);

    /**
     * Calculate the service name for a messaging time in queue synthetic span.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka, amqp,..)
     * @return the service name
     */
    @NonNull
    String timeInQueueService(@NonNull String messagingSystem);

    /**
     * Calculate the operation name for a messaging time in queue synthetic span.
     *
     * @param messagingSystem the messaging system (e.g. jms, kafka, amqp,..)
     * @return the operation name
     */
    @NonNull
    String timeInQueueOperation(@NonNull String messagingSystem);
  }

  interface ForPeerService {
    /**
     * Whenever the schema supports peer service calculation
     *
     * @return
     */
    boolean supports();

    /**
     * Calculate the tags to be added to a span to represent the peer service
     *
     * @param unsafeTags the span tags. Map che be mutated
     * @return the input tags
     */
    @NonNull
    Map<String, Object> tags(@NonNull Map<String, Object> unsafeTags);
  }

  interface ForServer {
    /**
     * Calculate the operation name for a server span.
     *
     * @param protocol the protocol used (e.g. http, soap, rmi ..)
     * @return the operation name
     */
    @NonNull
    String operationForProtocol(@NonNull String protocol);

    /**
     * Calculate the operation name for a server span.
     *
     * @param component the name of the instrumentation component
     * @return the operation name
     */
    @NonNull
    String operationForComponent(@NonNull String component);
  }
}
