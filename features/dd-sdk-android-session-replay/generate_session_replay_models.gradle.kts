import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateSessionReplayModelsTaskName = "generateSessionReplayModels"

tasks.register(
    generateSessionReplayModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/schemas"
    targetPackageName = "com.datadog.android.sessionreplay.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(
            generateSessionReplayModelsTaskName
        )
    tasks.withType(KotlinCompile::class.java) {
        dependsOn(
            generateSessionReplayModelsTaskName
        )
    }
}
