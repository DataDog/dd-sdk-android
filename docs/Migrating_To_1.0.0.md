# Migrating to 1.0.0

If you've been using the former SDK (version `0.1.x` or `0.2.x`), there are some breaking changes
introduced in version `1.0.0`, namely:

### Logger.Builder

#### Before

```java
    logger = new LoggerBuilder()
        .withName("my-application-name") // This would set the service name
        .withNetworkInfoLogging(this)
        .build("my-api-key");
```

#### After

```java
    Datadog.initialize(context, "my-api-key");

    // …

    logger = new Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName("android-sample-java") // Sets the service name
            .setLoggerName("my_logger") // Sets the logger name (within the service)
            .setLogcatLogsEnabled(true)
            .build();
```

#### Attributes

The attributes were created or removed with the `Logger.addField()` or `Logger.removeField()`
methods. These methods were rename for consistency purposes, and are now  `Logger.addAttribute()`
 and`Logger.removeAttribute()`, but they will behave the same way as the old ones.