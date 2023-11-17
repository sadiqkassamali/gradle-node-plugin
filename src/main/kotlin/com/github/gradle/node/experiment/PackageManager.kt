package com.github.gradle.node.experiment

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import javax.inject.Inject

abstract class PackageManager @Inject constructor(
    @get:Internal
    val name: String,
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val workDir: DirectoryProperty
) {
    @get:Input
    abstract val version: Property<String>
    @get:Internal
    abstract val enabled: Property<Boolean>
}
