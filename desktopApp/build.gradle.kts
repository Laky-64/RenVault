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
    implementation(libs.compose.components.resources)
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "RenVault"
            packageVersion = providers.gradleProperty("renvault.package_version").getOrElse("1.0.0")
            macOS {
                bundleID = "io.lauren.renvault"
            }
            linux {
                packageName = "io.lauren.renvault"
            }
        }
    }
}
