import io.lauren.renvault.gradle.RenVaultNativeExtension
import io.lauren.renvault.gradle.hostGoarch
import io.lauren.renvault.gradle.hostGoos
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("renvault-native")
}

val goHost = extensions.getByType<RenVaultNativeExtension>().host()

val appVersion = providers.gradleProperty("renvault.version").orElse("0.0.0")
val appVersionCode = providers.gradleProperty("renvault.version_code").orElse("0")
val appChannel = providers.gradleProperty("renvault.channel")
    .orElse(appVersion.zip(providers.environmentVariable("GITHUB_REF_TYPE").orElse("")) { version, ref ->
        when {
            "-" in version -> version.substringAfter('-').substringBefore('.')
            ref == "tag" -> "stable"
            else -> "dev"
        }
    })
val appCommit = providers.environmentVariable("GITHUB_SHA")
    .orElse(providers.exec { commandLine("git", "rev-parse", "HEAD"); isIgnoreExitValue = true }.standardOutput.asText)
    .map { it.trim().take(8) }
val goMod = rootProject.layout.projectDirectory.file("core/go.mod")
val generateVersion = tasks.register("generateAppVersion") {
    val out = layout.buildDirectory.dir("generated/version/kotlin")
    val version = appVersion
    val code = appVersionCode
    val channel = appChannel
    val commit = appCommit
    val mod = goMod
    inputs.property("version", version)
    inputs.property("code", code)
    inputs.property("channel", channel)
    inputs.property("commit", commit)
    inputs.file(mod)
    outputs.dir(out)
    doLast {
        val apple = Regex("""github\.com/Laky-64/appleservices (\S+)""").find(mod.asFile.readText())?.groupValues?.get(1).orEmpty()
        val file = out.get().file("io/lauren/renvault/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.lauren.renvault
            |
            |const val RvAppVersion: String = "${version.get()}"
            |
            |const val RvAppVersionCode: String = "${code.get()}"
            |
            |const val RvAppChannel: String = "${channel.get()}"
            |
            |const val RvAppCommit: String = "${commit.get()}"
            |
            |const val RvAppleServicesVersion: String = "$apple"
            |""".trimMargin(),
        )
    }
}


kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
       namespace = "io.lauren.renvault.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        val shared = "src/jvmSharedMain/kotlin"
        androidMain.get().kotlin.srcDir(shared)
        jvmMain.get().kotlin.srcDir(shared)

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.play.services.codeScanner)
            implementation(libs.androidx.work.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.jmdns)
        }
        commonMain.get().kotlin.srcDir(generateVersion)
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationCbor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.lauren.renvault.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

tasks.named<ProcessResources>("jvmProcessResources") {
    from(goHost.map { it.output }) {
        into("native/$hostGoos-$hostGoarch")
    }
}
