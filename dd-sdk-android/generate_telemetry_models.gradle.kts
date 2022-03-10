import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateTelemetryModelsTaskName = "generateTelemetryModelsFromJson"

tasks.register(
    generateTelemetryModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/telemetry"
    targetPackageName = "com.datadog.android.telemetry.model"
    ignoredFiles = arrayOf("_common-schema.json")
    inputNameMapping = mapOf(
        "debug-schema.json" to "DebugEvent",
        "error-schema.json" to "ErrorEvent"
    )
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(generateTelemetryModelsTaskName)
    tasks.withType(KotlinCompile::class.java) { dependsOn(generateTelemetryModelsTaskName) }
}
