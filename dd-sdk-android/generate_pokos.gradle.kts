import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin

val generateRumPokosTaskName: String = "generateJsonSchema2Poko"
val generateCorePokosTaskName: String = "generateCoreClasses"

tasks.register(
    generateRumPokosTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json"
    targetPackageName = "com.datadog.android.rum.model"
    ignoredFiles = arrayOf("_common-schema.json")
    inputNameMapping = mapOf(
        "action-schema.json" to "ActionEvent",
        "error-schema.json" to "ErrorEvent",
        "resource-schema.json" to "ResourceEvent",
        "view-schema.json" to "ViewEvent",
        "long_task-schema.json" to "LongTaskEvent"
    )
}

tasks.register(
    generateCorePokosTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/core-internal-schemas"
    targetPackageName = "com.datadog.android.core.model"
}

tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
    ?.dependsOn(generateRumPokosTaskName, generateCorePokosTaskName)

tasks.findByName("preBuild")
    ?.dependsOn(generateRumPokosTaskName, generateCorePokosTaskName)
