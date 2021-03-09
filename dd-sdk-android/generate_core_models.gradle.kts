import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateCoreModelsTaskName = "generateCoreModelsFromJson"

tasks.register(
    generateCoreModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/core"
    targetPackageName = "com.datadog.android.core.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(generateCoreModelsTaskName)
    tasks.withType(KotlinCompile::class.java) { dependsOn(generateCoreModelsTaskName) }
}
