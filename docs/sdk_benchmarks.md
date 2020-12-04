# SDK Usage Benchmarks

## Concept

The way we procured the data for our benchmarks was by profiling several popular open source Android applications 
([GoogleIO](https://github.com/google/iosched), [RedReader](https://github.com/QuantumBadger/RedReader)).
All profiling sessions were performed on a SAMSUNG GALAXY S9(SM-G960F) device running Android version 10.

## Benchmarks

For measuring the SDK performance and Network activity we played with each tested application main functionality for about 2 minutes. 
We tried to stay consistent in the use cases performed for both sessions (with Datadog SDK enabled and without).

#### Performance profiling 

##### GoogleIO

| Datadog SDK enabled | Max RAM | Max CPU | ENERGY consumption at peak |
|:-------------------:|:-------:|:-------:|:--------------------------:|
|         true        |  267 MB |   35%   |          < LIGHT           |
|        false        |  262 MB |   34%   |          < LIGHT           |

##### RedReader 

| Datadog SDK enabled | Max RAM | Max CPU | ENERGY consumption at peak |
|:-------------------:|:-------:|:-------:|:--------------------------:|
|         true        |  297 MB |   41%   |          < LIGHT           |
|        false        |  288 MB |   41%   |          < LIGHT          |


#### Network profiling

##### GoogleIO

| Endpoint                 | Network requests | Data sent | Data received | Time interval |
|--------------------------|------------------|-----------|---------------|---------------|
| Datadog RUM              | 1                | 14.48 Kb  | 31.96 Kb      | 2 minutes     |
| Datadog Tracing          | 1                | 3.96 Kb   | 35.40 Kb      | 2 minutes     |
| Datadog Log              | 1                | 13.33 Kb  | 31.76 Kb      | 2 minutes     |
| Application private requests     | 6                | 530 Kb    | 136.17 Kb     | 2 minutes     |


##### RedReader

For RedReader we could not auto - instrument the Logs sent inside the application hence the missing data for the **Datadog Logs** endpoint.

| Endpoint                 | Network requests | Data sent | Data received | Time interval |
|--------------------------|------------------|-----------|---------------|---------------|
| Datadog RUM              | 1                | 102.39 Kb | 32.76 Kb      | 2 minutes     |
| Datadog Tracing          | 1                | 21 Kb     | 32.26 Kb      | 2 minutes     |
| Datadog Logs             |                  |           |               |               |
| Application private requests     | 28               | 104 Kb    | 252 Kb        | 2 minutes     |


**Notes**

1. You can observe in our Network profiling data that the max number of requests per each Datadog endpoint in a 2 minutes interval is 1.
That is because we are using the keep alive capability in our OkHttp client to reuse the opened connections during a specific time interval.
2. To display the energy consumption data we are using as measurement unit: LIGHT, MEDIUM, HEAVY. Those are the energy consumption levels
   proposed by the Android Profiler tool.

