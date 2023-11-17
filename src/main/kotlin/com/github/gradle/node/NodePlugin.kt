package com.github.gradle.node

import com.github.gradle.node.bun.task.BunInstallTask
import com.github.gradle.node.bun.task.BunSetupTask
import com.github.gradle.node.bun.task.BunTask
import com.github.gradle.node.bun.task.BunxTask
import com.github.gradle.node.experiment.PluginModuleConfiguration
import com.github.gradle.node.npm.proxy.ProxySettings
import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.npm.task.NpmSetupTask
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask
import com.github.gradle.node.pnpm.task.PnpmInstallTask
import com.github.gradle.node.pnpm.task.PnpmSetupTask
import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeSetupTask
import com.github.gradle.node.task.NodeTask
import com.github.gradle.node.util.*
import com.github.gradle.node.variant.computeNodeArchiveDependency
import com.github.gradle.node.variant.computeNodeDir
import com.github.gradle.node.yarn.task.YarnInstallTask
import com.github.gradle.node.yarn.task.YarnSetupTask
import com.github.gradle.node.yarn.task.YarnTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.gradle.util.GradleVersion
import java.io.ByteArrayOutputStream
import java.io.File

class NodePlugin : Plugin<Project> {
    private lateinit var project: Project

    override fun apply(project: Project) {
//        if (GradleVersion.current() < MINIMAL_SUPPORTED_GRADLE_VERSION) {
//            project.logger.error("This version of the plugin requires $MINIMAL_SUPPORTED_GRADLE_VERSION or newer.")
//        }
        this.project = project
        val nodeExtension = NodeExtension.create(project)
        val container = project.objects.domainObjectContainer(PluginModuleConfiguration::class)
        nodeExtension.extensions.add("custom", container)
        configureNodeExtension(nodeExtension)
        project.extensions.create<PackageJsonExtension>(PackageJsonExtension.NAME, project)
        addGlobalTypes()
        addNodeTasks()
        addNpmTasks(nodeExtension, container)
        addYarnTasks(nodeExtension, container)
        addPnpmTasks(nodeExtension, container)
        addBunTasks(nodeExtension, container)
        addNpmRule(nodeExtension.enableTaskRules)
        addPnpmRule(nodeExtension.enableTaskRules)
        addYarnRule(nodeExtension.enableTaskRules)

        project.afterEvaluate {
            if (nodeExtension.download.get()) {
                nodeExtension.distBaseUrl.orNull?.let { addRepository(it, nodeExtension.allowInsecureProtocol.orNull) }
                configureNodeSetupTask(nodeExtension)
            }
        }
    }

    private fun addBunTasks(nodeExtension: NodeExtension, container: NamedDomainObjectContainer<PluginModuleConfiguration>) {
        val manager = project.objects.newInstance(PluginModuleConfiguration::class, "bun", nodeExtension.bunWorkDir)
        manager.enabled.convention(true)
        manager.version.convention(nodeExtension.bunVersion)
        container.add(manager)

        project.tasks.register<BunSetupTask>(BunSetupTask.NAME) {
            version.convention(manager.version)
            enabled = manager.enabled.get()
            manager(manager)
        }
        project.tasks.register<BunInstallTask>(BunInstallTask.NAME) {
            enabled = manager.enabled.get()
        }
    }

    private fun addPnpmTasks(nodeExtension: NodeExtension, container: NamedDomainObjectContainer<PluginModuleConfiguration>) {
        val manager = project.objects.newInstance(PluginModuleConfiguration::class, "pnpm", nodeExtension.pnpmWorkDir)
        manager.enabled.convention(true)
        manager.version.convention(nodeExtension.pnpmVersion)
        container.add(manager)

        project.tasks.register<PnpmSetupTask>(PnpmSetupTask.NAME) {
            version.convention(manager.version)
            enabled = manager.enabled.get()
            manager(manager)
        }
        project.tasks.register<PnpmInstallTask>(PnpmInstallTask.NAME) {
            enabled = manager.enabled.get()
        }

    }

    private fun addYarnTasks(nodeExtension: NodeExtension, container: NamedDomainObjectContainer<PluginModuleConfiguration>) {
        val manager = project.objects.newInstance(PluginModuleConfiguration::class, "yarn", nodeExtension.yarnWorkDir)
        manager.enabled.convention(true)
        manager.version.convention(nodeExtension.yarnVersion)
        container.add(manager)

        project.tasks.register<YarnSetupTask>(YarnSetupTask.NAME).configure {
            version.convention(manager.version)
            enabled = manager.enabled.get()
            manager(manager)
        }
        project.tasks.register<YarnInstallTask>(YarnInstallTask.NAME) {
            enabled = manager.enabled.get()
        }
    }

    private fun addNodeTasks() {
        project.tasks.register<NodeSetupTask>(NodeSetupTask.NAME)
    }

    private fun addNpmTasks(nodeExtension: NodeExtension, container: NamedDomainObjectContainer<PluginModuleConfiguration>) {
        val manager = project.objects.newInstance(PluginModuleConfiguration::class, "npm", nodeExtension.npmWorkDir)
        manager.enabled.convention(true)
        manager.version.convention(nodeExtension.npmVersion)
        container.add(manager)

        project.tasks.register<NpmSetupTask>(NpmSetupTask.NAME) {
            version.convention(manager.version)
            enabled = manager.enabled.get()
            manager(manager)
        }
        project.tasks.register<NpmInstallTask>(NpmInstallTask.NAME) {
            enabled = manager.enabled.get()
        }
    }

    @SuppressWarnings("deprecation")
    private fun configureNodeExtension(extension: NodeExtension) {
        addPlatform(extension)
        with(extension.resolvedNodeDir) {
            set(computeNodeDir(extension))
            finalizeValueOnRead()
        }
    }

    private fun addPlatform(extension: NodeExtension) {
        val uname = {
            if (GradleVersion.current() >= GradleVersion.version("7.5")) {
                val cmd = project.providers.exec {
                    this.executable = "uname"
                    this.args = listOf("-m")
                }
                cmd.standardOutput.asText.get().trim()
            } else {
                val out = ByteArrayOutputStream()
                val cmd = project.exec {
                    this.executable = "uname"
                    this.args = listOf("-m")
                    this.standardOutput = out
                }

                cmd.assertNormalExitValue()
                out.toString().trim()
            }
        }
        val name = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        val platform = parsePlatform(name, arch, uname)
        extension.resolvedPlatform.set(platform)
    }

    private fun addGlobalTypes() {
        addGlobalType<NodeTask>()
        addGlobalType<NpmTask>()
        addGlobalType<NpxTask>()
        addGlobalType<PnpmTask>()
        addGlobalType<YarnTask>()
        addGlobalType<BunTask>()
        addGlobalType<BunxTask>()
        addGlobalType<ProxySettings>()
    }

    private inline fun <reified T> addGlobalType() {
        project.extensions.extraProperties[T::class.java.simpleName] = T::class.java
    }

    private fun addNpmRule(enableTaskRules: Property<Boolean>) { // note this rule also makes it possible to specify e.g. "dependsOn npm_install"
        project.tasks.addRule("Pattern: \"npm_<command>\": Executes an NPM command.") {
            val taskName = this
            if (taskName.startsWith("npm_") && enableTaskRules.get()) {
                project.tasks.create<NpmTask>(taskName) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    npmCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        dependsOn(NpmInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addPnpmRule(enableTaskRules: Property<Boolean>) { // note this rule also makes it possible to specify e.g. "dependsOn npm_install"
        project.tasks.addRule("Pattern: \"pnpm_<command>\": Executes an PNPM command.") {
            val taskName = this
            if (taskName.startsWith("pnpm_") && enableTaskRules.get()) {
                project.tasks.register<PnpmTask>(taskName) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    pnpmCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        dependsOn(PnpmInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addYarnRule(enableTaskRules: Property<Boolean>) { // note this rule also makes it possible to specify e.g. "dependsOn yarn_install"
        project.tasks.addRule("Pattern: \"yarn_<command>\": Executes an Yarn command.") {
            val taskName = this
            if (taskName.startsWith("yarn_") && enableTaskRules.get()) {
                project.tasks.create<YarnTask>(taskName) {
                    val tokens = taskName.split("_").drop(1) // all except first
                    yarnCommand.set(tokens)
                    if (tokens.first().equals("run", ignoreCase = true)) {
                        dependsOn(YarnInstallTask.NAME)
                    }
                }
            }
        }
    }

    private fun addRepository(distUrl: String, allowInsecureProtocol: Boolean?) {
        project.repositories.ivy {
            name = "Node.js"
            setUrl(distUrl)
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
            allowInsecureProtocol?.let { isAllowInsecureProtocol = it }
        }
    }

    private fun configureNodeSetupTask(nodeExtension: NodeExtension) {
        project.tasks.withType<NodeSetupTask>().configureEach {
            nodeDir.set(nodeExtension.resolvedNodeDir)
            val archiveFileProvider = computeNodeArchiveDependency(nodeExtension)
                    .map { nodeArchiveDependency ->
                        resolveNodeArchiveFile(nodeArchiveDependency)
                    }
            nodeArchiveFile.set(project.layout.file(archiveFileProvider))
        }
    }

    private fun resolveNodeArchiveFile(name: String): File {
        val dependency = project.dependencies.create(name)
        val configuration = project.configurations.detachedConfiguration(dependency)
        configuration.isTransitive = false
        return configuration.resolve().single()
    }

    companion object {
        val MINIMAL_SUPPORTED_GRADLE_VERSION: GradleVersion = GradleVersion.version("6.6")
        const val NODE_GROUP = "Node"
        const val NPM_GROUP = "npm"
        const val PNPM_GROUP = "pnpm"
        const val YARN_GROUP = "Yarn"
        const val BUN_GROUP = "Bun"
    }
}
