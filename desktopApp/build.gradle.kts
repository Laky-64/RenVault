import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

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

val appId = providers.gradleProperty("renvault.id").get()
val appName = providers.gradleProperty("renvault.name").get()

compose.desktop {
    application {
        mainClass = "$appId." + (System.getProperty("renvault.main") ?: "MainKt")
        javaHome = runtime.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = appName
            packageVersion = providers.gradleProperty("renvault.package_version").getOrElse("1.0.0")
            macOS {
                bundleID = appId
                iconFile.set(project.file("icons/renvault.icns"))
            }
            windows {
                upgradeUuid = "737d67c8-f74c-4bc3-8e60-847cdd4be676"
                iconFile.set(project.file("icons/renvault.ico"))
                menuGroup = appName
                shortcut = true
                menu = true
                dirChooser = true
            }
            linux {
                packageName = appId
                iconFile.set(project.file("icons/renvault.png"))
            }
        }
    }
}

tasks.withType<AbstractJPackageTask>().matching { it.targetFormat == TargetFormat.Msi }.configureEach {
    val distributable = tasks.named<AbstractJPackageTask>("createDistributable")
    dependsOn(distributable)
    appImage.set(distributable.flatMap { task -> task.destinationDir.zip(task.packageName) { dir, name -> dir.dir(name) } })
}

tasks.withType<AbstractJPackageTask>().matching { it.targetFormat == TargetFormat.Dmg }.configureEach {
    freeArgs.addAll("--icon", project.file("icons/renvault.icns").absolutePath)
}
