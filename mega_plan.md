This SDK has a lot of internal logging done using @dd-sdk-android-core/src/main/kotlin/com/datadog/android/api/InternalLogger.kt. 

It is scattered all over the place, there is no way currently to know what logs exist. I want to implement the following solution. 

Each gradle module needs to have a yaml file that will represent a list of logs that this module wants to do. Each entry needs to specify:
1. A logs message.
2. A logs id. It is a string that is globally unique. 
3. Additional properties of the log. I want to support primitive types, enums and nested types (like a struct that contains an int and another struct, etc).
4. sampleRate if it exists.
5. list of targets (USER, TELEMETRY, MAINTAINER)

I want to have a gradle plugin implemented in buildSrc next to other gradle plugins that can be applied to the module if needed. This plugin needs to look at the yaml file called logs_config.yaml in the root directory of the module and if it exists it should parse it and generate the kotlin code for logging. This code should be a bunch of extension functions of InternalLogger class. The name of the function should be derived from the id. The arguments are the additional properties, but as generated strong kotlin types.

I want you to first think about the possible yaml format. Let's focus on "[Mobile Metric] RUM View Ended" telemetry log as an example. Then after we discuss it, I'll tell you to discuss the gradle plugin with me, for example what the generate function for this telemetry log will look like. Then I'll ask you to implement this (probably only for this log). Then I'll see what was produced and we'll iterate.