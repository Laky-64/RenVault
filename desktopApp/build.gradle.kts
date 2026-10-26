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
}

val runtime = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
    vendor = JvmVendorSpec.JETBRAINS
}

compose.desktop {
    application {
        mainClass = System.getProperty("renvault.main") ?: "io.lauren.renvault.MainKt"
        javaHome = runtime.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "RenVault"
            packageVersion = providers.gradleProperty("renvault.package_version").getOrElse("1.0.0")
            macOS {
                bundleID = "io.lauren.renvault"
            }
            windows {
                upgradeUuid = "737d67c8-f74c-4bc3-8e60-847cdd4be676"
                menuGroup = "RenVault"
                shortcut = true
                menu = true
                dirChooser = true
            }
            linux {
                packageName = "io.lauren.renvault"
            }
        }
    }
}
