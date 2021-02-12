import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin

tasks.register(
    "generateJsonSchema2Poko",
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

tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
    ?.dependsOn("generateJsonSchema2Poko")

tasks.named("preBuild") { dependsOn("generateJsonSchema2Poko") }
