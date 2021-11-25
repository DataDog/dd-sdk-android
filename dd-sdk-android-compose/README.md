# Datadog Integration for Jetpack Compose

## Getting Started

To include the Datadog integration for [Jetpack Compose][1] in your project, add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-compose:<latest-version>"
}
```

### Initial setup

Before using the SDK, set up the library with your application
context, client token, and application ID.
To generate a client token and an application ID, check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val configuration = Configuration.Builder(
            rumEnabled = true,
            ...
        )
            .trackInteractions()
            .useViewTrackingStrategy(strategy)
            ...
            .build()

        val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
        Datadog.initialize(this, credentials, configuration, trackingConsent)

        val monitor = RumMonitor.Builder().build()
        GlobalRum.registerIfAbsent(monitor)
   }
}
```

#### Navigation for Compose support

**Support of Navigation for Compose is experimental, because `androidx.navigation:navigation-compose` is still in alpha, so overall API may be a subject to change.**

If you are migrating your existing app to Jetpack Compose, and use fragment-based Navigation as it is recommended in the [guide][2], continue to use `useViewTrackingStrategy` with the strategy which suits you best.

If you have an app with only Compose elements, you can add support of view tracking for Navigation for Compose in the following way when `NavController` is created:

```kotlin
val navController = rememberNavController().apply {
    DatadogViewTrackingEffect(navController = this, trackArguments = ..., destinationPredicate = ...)
}
```

You also must reject Activities or Fragments, which are hosts of Compose views during `useViewTrackingStrategy` setup, so that they are not counted as views.
Here is an example in case of `ActivityViewTrackingStrategy` usage:

```kotlin
val configuration = Configuration.Builder(
    rumEnabled = true,
    ...
)
    .useViewTrackingStrategy(
        ActivityViewTrackingStrategy(
            trackExtras = ...,
            componentPredicate = object : ComponentPredicate<Activity> {
                override fun accept(component: Activity): Boolean {
                    return component !is MyComposeActivity
                }

                override fun getViewName(component: Activity): String? = null
            })
    )
    ...
    .build()
```

#### Action tracking

There is no automated instrumentation support for the action tracking in Jetpack Compose. However, report clicks using `trackClick` method using this example:

```kotlin
Button(
    onClick = trackClick(targetName = "Open View") { ...click logic... }
    ...
) {
    ...layout...
}
```

Swipe and scroll events can be reported by using `TrackInteractionsEffect`. Here is an example of its usage with `Modifier.swipeable`:

```kotlin

val swipeableState = rememberSwipeableState(...)
val swipeOrientation = Orientation.Horizontal

val interactionSource = remember {
    MutableInteractionSource()
}.apply {
    TrackInteractionEffect(
        targetName = "Item row",
        interactionSource = this,
        interactionType = InteractionType.Swipe(
            swipeableState,
            orientation = swipeOrientation
        ),
        attributes = mapOf("foo" to "bar")
    )
}

Box(
    modifier = Modifier
        .swipeable(
            interactionSource = interactionSource,
            state = swipeableState,
            orientation = swipeOrientation,
            ...
        )
        .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
) {
    ...
}
```

## Contributing

For details on contributing, read the
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://developer.android.com/jetpack/compose
[2]: https://developer.android.com/jetpack/compose/navigation#interoperability
