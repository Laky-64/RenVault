import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractCreateDistributableTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.time.LocalDate

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
val appSummary = providers.gradleProperty("renvault.summary").get()
val appVendor = providers.gradleProperty("renvault.vendor").get()
val appEmail = providers.gradleProperty("renvault.email").get()
val appHomepage = providers.gradleProperty("renvault.homepage").get()
val appLicense = providers.gradleProperty("renvault.license").get()
val appCopyright = "Copyright (C) $appVendor"
val appVersion = providers.gradleProperty("renvault.version").getOrElse("1.0.0")
val appPackageVersion = providers.gradleProperty("renvault.package_version").getOrElse("1.0.0")

compose.desktop {
    application {
        mainClass = "$appId." + (System.getProperty("renvault.main") ?: "MainKt")
        javaHome = runtime.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = appName
            packageVersion = appPackageVersion
            description = appSummary
            vendor = appVendor
            copyright = appCopyright
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
    val distributable = tasks.named<AbstractCreateDistributableTask>("createDistributable")
    val name = appName
    dependsOn(distributable)
    appImage.set(distributable.flatMap { task -> task.destinationDir.dir(name) })
}

tasks.withType<AbstractJPackageTask>().matching { it.targetFormat == TargetFormat.Dmg }.configureEach {
    freeArgs.addAll("--icon", project.file("icons/renvault.icns").absolutePath)
}

val linuxShare = layout.buildDirectory.dir("linux/share")
val linuxResources = layout.buildDirectory.dir("linux/resources")
val linuxTokens = mapOf(
    "__ID__" to appId,
    "__NAME__" to appName,
    "__SUMMARY__" to appSummary,
    "__VENDOR__" to appVendor,
    "__HOMEPAGE__" to appHomepage,
    "__LICENSE__" to appLicense,
    "__VERSION__" to appVersion,
    "__SHARE__" to linuxShare.get().asFile.absolutePath,
)

fun CopySpec.fill(tokens: Map<String, String>) = filter { line -> tokens.entries.fold(line) { text, (key, value) -> text.replace(key, value) } }

val prepareLinuxShare = tasks.register<Sync>("prepareLinuxShare") {
    val tokens = linuxTokens + ("__DATE__" to LocalDate.now().toString())
    val icon = "$appId.png"
    val metainfo = "$appId.metainfo.xml"
    val desktop = "$appId.desktop"
    inputs.property("tokens", tokens)
    into(linuxShare)
    from("linux/app.metainfo.xml") {
        into("metainfo")
        rename { metainfo }
        fill(tokens)
    }
    from("linux/app.desktop") {
        into("applications")
        rename { desktop }
        fill(tokens)
    }
    from("icons/renvault.png") {
        into("icons/hicolor/512x512/apps")
        rename { icon }
    }
}

val prepareLinuxResources = tasks.register<Sync>("prepareLinuxResources") {
    val tokens = linuxTokens
    val spec = "$appId.spec"
    inputs.property("tokens", tokens)
    into(linuxResources)
    from("linux/app.spec") {
        rename { spec }
        fill(tokens)
    }
}

fun linuxPackage(type: String) = tasks.register<Exec>("packageLinux${type.replaceFirstChar { it.uppercase() }}") {
    val distributable = tasks.named<AbstractCreateDistributableTask>("createDistributable")
    val name = appName
    dependsOn(distributable, prepareLinuxShare, prepareLinuxResources)
    val image = distributable.flatMap { task -> task.destinationDir.dir(name) }
    val output = layout.buildDirectory.dir("compose/binaries/main/$type")
    val share = linuxShare
    val resources = linuxResources
    val common = listOf(
        "--type", type,
        "--name", appName,
        "--app-version", appPackageVersion,
        "--description", appSummary,
        "--vendor", appVendor,
        "--copyright", appCopyright,
        "--about-url", appHomepage,
        "--linux-package-name", appId,
        "--linux-app-category", "utils",
    ) + when (type) {
        "rpm" -> listOf("--resource-dir", resources.get().asFile.absolutePath, "--linux-rpm-license-type", appLicense)
        else -> listOf("--linux-deb-maintainer", appEmail)
    }
    inputs.dir(image)
    inputs.dir(linuxShare)
    inputs.dir(linuxResources)
    outputs.dir(output)
    executable = runtime.get().metadata.installationPath.file("bin/jpackage").asFile.absolutePath
    argumentProviders.add { common + listOf("--app-image", image.get().asFile.absolutePath, "--dest", output.get().asFile.absolutePath) }
    doFirst { output.get().asFile.deleteRecursively() }
    if (type == "deb") {
        doLast {
            fun run(vararg command: String) = check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0)
            val deb = output.get().asFile.listFiles { file -> file.extension == "deb" }.orEmpty().single()
            val root = temporaryDir.resolve("root")
            root.deleteRecursively()
            run("dpkg-deb", "-R", deb.absolutePath, root.absolutePath)
            share.get().asFile.copyRecursively(root.resolve("usr/share"), overwrite = true)
            run("dpkg-deb", "--root-owner-group", "-Zxz", "-b", root.absolutePath, deb.absolutePath)
            root.deleteRecursively()
        }
    }
}

val linuxPackages = listOf(linuxPackage("rpm"), linuxPackage("deb"))

if (System.getProperty("os.name").orEmpty().startsWith("Linux")) {
    tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach { dependsOn(linuxPackages) }
}
