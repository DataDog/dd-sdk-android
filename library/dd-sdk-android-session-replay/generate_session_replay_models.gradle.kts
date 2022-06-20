import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateSessionReplayModelsTaskName = "generateSessionReplayModels"
val generateSessionReplayMobileModelsTaskName = "generateSessionReplayMobileModels"

tasks.register(
    "generateSessionReplayModels",
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/session-replay"
    targetPackageName = "com.datadog.android.sessionreplay.model"
    ignoredFiles = arrayOf(
        "_common-record-schema.json",
        "focus-record-schema.json",
        "metadata-record-schema.json",
        "view-end-record-schema.json"
    )
}

tasks.register(
    "generateSessionReplayMobileModels",
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/session-replay/mobile"
    targetPackageName = "com.datadog.android.sessionreplay.model"
    ignoredFiles = arrayOf(
        "_common-wireframe-schema.json",
        "_common-shape-wireframe-schema.json",
        "_common-shape-wireframe-update-schema.json",
        "_common-wireframe-schema.json",
        "_common-wireframe-update-schema.json",
        "mutation-data-schema.json",
        "viewport-resize-data-schema.json",
        "touch-data-schema.json",
        "shape-wireframe-schema.json",
        "text-wireframe-schema.json",
        "shape-wireframe-update-schema.json",
        "text-wireframe-update-schema.json"
    )
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(
            generateSessionReplayModelsTaskName,
            generateSessionReplayMobileModelsTaskName
        )
    tasks.withType(KotlinCompile::class.java) {
        dependsOn(
            generateSessionReplayModelsTaskName,
            generateSessionReplayMobileModelsTaskName
        )
    }
}
