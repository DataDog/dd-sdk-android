import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateLogModelsTaskName = "generateLogModelsFromJson"

tasks.register(
    generateLogModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/log"
    targetPackageName = "com.datadog.android.log.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(generateLogModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateLogModelsTaskName)
    }
}
