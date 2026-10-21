package io.lauren.renvault.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class GoBuildTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val goExecutable: Property<String>

    @get:Input
    abstract val goos: Property<String>

    @get:Input
    abstract val goarch: Property<String>

    @get:Input
    abstract val buildMode: Property<String>

    @get:Input
    abstract val packagePath: Property<String>

    @get:Input
    @get:Optional
    abstract val compiler: Property<String>

    @get:Input
    abstract val tags: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val linkerFlags: Property<String>

    @get:Input
    abstract val extraEnvironment: MapProperty<String, String>

    @get:Internal
    abstract val moduleDirectory: DirectoryProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        group = "renvault"
        description = "Compiles the Go core into a native library"
    }

    @TaskAction
    fun compile() {
        val binary = output.get().asFile
        binary.parentFile.mkdirs()

        val arguments = mutableListOf("build", "-buildmode=${buildMode.get()}", "-trimpath")
        val selected = tags.get()
        if (selected.isNotEmpty()) {
            arguments += "-tags=${selected.joinToString(",")}"
        }
        linkerFlags.orNull?.takeIf(String::isNotEmpty)?.let { arguments += "-ldflags=$it" }
        arguments += listOf("-o", binary.absolutePath, packagePath.get())

        execOperations.exec {
            executable = goExecutable.get()
            workingDir = moduleDirectory.get().asFile
            args = arguments
            environment("GOOS", goos.get())
            environment("GOARCH", goarch.get())
            environment("CGO_ENABLED", "1")
            compiler.orNull?.let { environment("CC", it) }
            extraEnvironment.get().forEach(::environment)
        }
    }
}
