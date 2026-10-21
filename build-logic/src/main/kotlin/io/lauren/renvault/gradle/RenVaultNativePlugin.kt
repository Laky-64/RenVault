package io.lauren.renvault.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

data class AndroidAbi(
    val identifier: String,
    val goarch: String,
    val toolchainPrefix: String,
)

const val androidLinkerFlags = "-extldflags=-Wl,-z,max-page-size=16384"

val androidAbis = listOf(
    AndroidAbi("arm64-v8a", "arm64", "aarch64-linux-android"),
    AndroidAbi("armeabi-v7a", "arm", "armv7a-linux-androideabi"),
    AndroidAbi("x86_64", "amd64", "x86_64-linux-android"),
)

abstract class RenVaultNativeExtension @Inject constructor(private val project: Project) {

    abstract val coreDirectory: DirectoryProperty
    abstract val goExecutable: Property<String>
    abstract val ndkDirectory: DirectoryProperty
    abstract val apiLevel: Property<Int>

    private val sources: Provider<FileTree>
        get() = coreDirectory.map { directory ->
            directory.asFileTree.matching {
                include("**/*.go", "**/*.c", "**/*.h", "go.mod", "go.sum")
            }
        }

    fun android(
        identifiers: Set<String> = setOf("arm64-v8a", "x86_64"),
    ): List<TaskProvider<GoBuildTask>> = androidAbis.filter { it.identifier in identifiers }.map { abi ->
        val prefix = abi.toolchainPrefix
        val clang = ndkDirectory.zip(apiLevel) { ndk, level ->
            ndk.file("toolchains/llvm/prebuilt/$ndkHostTag/bin/$prefix$level-clang").asFile.absolutePath
        }

        register("goBuildAndroid${abi.goarch.replaceFirstChar(Char::uppercase)}") {
            goos.set("android")
            goarch.set(abi.goarch)
            buildMode.set("c-shared")
            compiler.set(clang)
            linkerFlags.set(androidLinkerFlags)
            output.set(
                project.layout.buildDirectory.file("renvault/abi/${abi.identifier}/librenvault.so")
            )
            if (abi.goarch == "arm") {
                extraEnvironment.put("GOARM", "7")
            }
        }
    }

    fun androidJniLibs(
        identifiers: Set<String> = setOf("arm64-v8a", "x86_64"),
    ): TaskProvider<GoJniLibsTask> {
        val builds = android(identifiers)
        return project.tasks.register<GoJniLibsTask>("goBuildAndroidJniLibs") {
            libraries.from(builds.map { build -> build.flatMap { it.output } })
            outputDirectory.set(project.layout.buildDirectory.dir("renvault/jniLibs"))
        }
    }

    fun host(): TaskProvider<GoBuildTask> {
        val includes = project.providers.systemProperty("java.home").map { home ->
            "-I$home/include -I$home/include/$jniHeaderDirectory"
        }

        return register("goBuildHost") {
            goos.set(hostGoos)
            goarch.set(hostGoarch)
            buildMode.set("c-shared")
            tags.set(listOf("jni"))
            extraEnvironment.put("CGO_CFLAGS", includes)
            output.set(
                project.layout.buildDirectory.file("renvault/native/$hostGoos-$hostGoarch/$hostLibraryName")
            )
        }
    }

    fun iosStatic(goarch: String, name: String): TaskProvider<GoBuildTask> =
        register("goBuildIos${name.replaceFirstChar(Char::uppercase)}") {
            goos.set("ios")
            this.goarch.set(goarch)
            buildMode.set("c-archive")
            output.set(
                project.layout.buildDirectory.file("renvault/ios/$name/librenvault.a")
            )
        }

    private fun register(
        name: String,
        configure: GoBuildTask.() -> Unit,
    ): TaskProvider<GoBuildTask> = project.tasks.register<GoBuildTask>(name) {
        sources.from(this@RenVaultNativeExtension.sources)
        moduleDirectory.set(coreDirectory)
        goExecutable.set(this@RenVaultNativeExtension.goExecutable)
        packagePath.set("./native")
        tags.convention(emptyList())
        configure()
    }
}

class RenVaultNativePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "renvaultNative",
            RenVaultNativeExtension::class.java,
            project,
        )

        extension.coreDirectory.convention(
            project.rootProject.layout.projectDirectory.dir("core")
        )
        extension.goExecutable.convention(resolveGo(project))
        extension.ndkDirectory.convention(
            project.rootProject.layout.projectDirectory.dir(resolveNdk(project))
        )
        extension.apiLevel.convention(24)
    }

    private fun resolveGo(project: Project): Provider<String> {
        val fromLocal = localProperty(project, "go.dir").map { "$it/bin/go" }
        val fromEnvironment = project.providers.environmentVariable("GO_HOME").map { "$it/bin/go" }
        return fromLocal.orElse(fromEnvironment).orElse("go")
    }

    private fun resolveNdk(project: Project): Provider<String> {
        val version = localProperty(project, "ndk.version").orElse(defaultNdkVersion)
        val explicit = localProperty(project, "ndk.dir")
        val fromSdk = localProperty(project, "sdk.dir").flatMap { sdk -> version.map { "$sdk/ndk/$it" } }
        val fromEnvironment = project.providers.environmentVariable("ANDROID_HOME")
            .flatMap { home -> version.map { "$home/ndk/$it" } }
        return explicit.orElse(fromSdk).orElse(fromEnvironment)
    }

    private fun localProperty(project: Project, key: String): Provider<String> =
        project.providers
            .fileContents(project.rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .map { text ->
                text.lineSequence()
                    .map(String::trim)
                    .firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter('=')
                    ?.trim()
                    .orEmpty()
            }
            .filter(String::isNotEmpty)

    private companion object {
        const val defaultNdkVersion = "27.2.12479018"
    }
}
