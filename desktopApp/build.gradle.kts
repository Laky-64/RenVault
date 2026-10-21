import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.jbr.api)
}

val runtime = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.JETBRAINS
}

compose.desktop {
    application {
        mainClass = System.getProperty("renvault.main") ?: "io.lauren.renvault.MainKt"
        javaHome = runtime.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.lauren.renvault"
            packageVersion = "1.0.0"
        }
    }
}