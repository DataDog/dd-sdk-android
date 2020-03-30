# Datadog SDK for Android

> A client-side Android library to interact with Datadog.

## Getting Started

See the dedicated [Datadog Android log collection documentation](http://docs.datadoghq.com/logs/log_collection/android) to learn how to forward logs from your Android application to Datadog.

### Migrating from earlier versions

If you are using SDK version `0.2.5` or lower, take a look at the [Migration Guide](docs/Migrating_To_1.0.0.md). There are some breaking changes introduced in version `1.0.0`.

### Integrating with Timber

If your existing codebase is already using Timber, you can migrate to Datadog easily by using the [dedicated library](dd-sdk-android-timber/README.md).

## Looking up your logs

When you open your console in Datadog, navigate to the Logs section. In the search bar, type `source:mobile`. This filters your logs to only show the ones coming from mobile applications (Android and iOS).

![Datadog Mobile Logs](docs/images/screenshot.png)

## Troubleshooting

If you encounter any issue when using the Datadog SDK for Android, please take a look at 
the [troubleshooting checklist](docs/TROUBLESHOOTING.md), or at 
the existing [issues](https://github.com/DataDog/dd-sdk-android/issues?q=is%3Aissue).

## Contributing

Pull requests are welcome. First, open an issue to discuss what you would like to change. For more information, read the [Contributing Guide](CONTRIBUTING.md).

## License

[Apache License, v2.0](LICENSE)
