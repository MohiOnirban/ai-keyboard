import java.net.URI

plugins {
    id("com.android.application") version "9.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

val liveSmoke = providers.gradleProperty("kothaLiveSmoke").orNull == "true"
val configuredBackend = providers.gradleProperty("kothaBackendUrl").orElse("").get().trim().trimEnd('/')
if (configuredBackend.isNotEmpty()) {
    val url = URI.create(configuredBackend)
    require(url.host != null && url.userInfo == null && url.query == null && url.fragment == null)
    require(url.scheme == "https" || url.scheme == "http" && url.host in setOf("10.0.2.2", "127.0.0.1", "localhost")) {
        "The AI backend must use HTTPS. Android debug builds also support emulator and loopback HTTP."
    }
}

android {
    namespace = "com.kothakeys.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kothakeys.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (liveSmoke) applicationIdSuffix = ".livesmoke"
            val endpoint = configuredBackend.ifEmpty { "http://10.0.2.2:4000" }
            buildConfigField("String", "BACKEND_URL", "\"$endpoint\"")
        }
        release {
            val endpoint = configuredBackend.takeIf { it.startsWith("https://") }.orEmpty()
            buildConfigField("String", "BACKEND_URL", "\"$endpoint\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets["main"].kotlin.directories.add(rootProject.file("../../shared/android").absolutePath)
    if (liveSmoke) {
        sourceSets["androidTest"].kotlin.directories.add("src/liveSmoke/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

abstract class CopyPhoneticAssets : DefaultTask() {
    @get:InputFile
    abstract val rules: RegularFileProperty

    @get:InputDirectory
    abstract val avro: DirectoryProperty

    @get:InputFile
    abstract val adapter: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copyRules() {
        output.get().asFile.deleteRecursively()
        val rulesFile = rules.get().asFile
        rulesFile.copyTo(output.file(rulesFile.name).get().asFile, overwrite = true)
        listOf("grammar.json", "avro-lib.js", "LICENSE.txt", "NOTICE.txt").forEach { item ->
            avro.file(item).get().asFile.copyTo(output.file("avro/$item").get().asFile, overwrite = true)
        }
        adapter.get().asFile.copyTo(output.file("avro/Avro.kt").get().asFile, overwrite = true)
    }
}

val copyPhonetic = tasks.register<CopyPhoneticAssets>("copyPhoneticAssets") {
    rules.set(rootProject.file("../../shared/phonetic.json"))
    avro.set(rootProject.file("../../shared/avro"))
    adapter.set(rootProject.file("../../shared/android/Avro.kt"))
    output.set(layout.buildDirectory.dir("generated/phoneticAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyPhonetic, CopyPhoneticAssets::output)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
