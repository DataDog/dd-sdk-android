# Timeseries model generation

The `RumTimeseriesMemoryEvent` and `RumTimeseriesCpuEvent` classes are generated at build time
from the JSON schemas in `src/main/json/rum/`. They are **not** committed to source.

Before building or running tests on this branch, generate the models:

```bash
./gradlew :features:dd-sdk-android-rum:generateRumModelsFromJson
```

The generated classes land in `build/generated/json2kotlin/main/kotlin/com/datadog/android/rum/model/`.
