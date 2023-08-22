import com.android.build.gradle.tasks.SourceJarTask
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
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(
            generateSessionReplayModelsTaskName
        )
    tasks.withType(KotlinCompile::class.java) {
        dependsOn(
            generateSessionReplayModelsTaskName
        )
    }

    // need to add an explicit dependency, otherwise there is an error during publishing
    // Task ':features:dd-sdk-android-session-replay:sourceReleaseJar' uses this output of task
    // ':features:dd-sdk-android-session-replay:generateSessionReplayModelsFromJson' without
    // declaring an explicit or implicit dependency
    //
    // it is not needed for other modules with similar model generation, because they use KSP,
    // and KSP plugin see to establish link between sourcesJar and "generated" folder in general
    tasks.withType(SourceJarTask::class.java) {
        dependsOn(generateSessionReplayModelsTaskName)
    }
}
