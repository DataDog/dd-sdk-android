import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateTraceModelsTaskName = "generateTraceModelsFromJson"

tasks.register(
    generateTraceModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/trace"
    targetPackageName = "com.datadog.android.tracing.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        ?.dependsOn(generateTraceModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateTraceModelsTaskName)
    }
}
