package io.lauren.renvault.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class GoJniLibsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val libraries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileOperations: FileSystemOperations

    init {
        group = "renvault"
        description = "Collects the Go libraries into a jniLibs layout"
    }

    @TaskAction
    fun collect() {
        val destination = outputDirectory.get().asFile
        fileOperations.delete { delete(destination) }

        libraries.files.forEach { library ->
            fileOperations.copy {
                from(library)
                into(destination.resolve(library.parentFile.name))
            }
        }
    }
}
