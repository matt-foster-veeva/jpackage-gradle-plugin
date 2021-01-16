/*
 Copyright (c) Petr Panteleyev. All rights reserved.
 Licensed under the BSD license. See LICENSE file in the project root for full license information.
 */
package org.panteleyev.jpackage

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

open class JPackageTask : DefaultTask() {
    companion object {
        const val EXECUTABLE = "jpackage"
    }

    @Input
    var verbose = false

    @Input
    var type = ImageType.DEFAULT

    @Input
    var appName: String = ""

    @Input
    var appVersion = project.version as String

    @Input
    var vendor = ""

    @Input
    var icon = ""

    @Input
    var runtimeImage = ""

    @Input
    var input = ""

    @Input
    var installDir = ""

    @Input
    var destination = ""

    @Input
    var module = ""

    @Input
    var mainClass = ""

    @Input
    var mainJar = ""

    @Input
    var copyright = ""

    @Input
    var appDescription = ""

    @Input
    var modulePath = ""

    @Input
    var javaOptions: List<String> = ArrayList()

    @Input
    var arguments: List<String> = ArrayList()

    // Windows specific parameters
    @Input
    var winMenu = false

    @Input
    var winDirChooser = false

    @Input
    var winUpgradeUuid = ""

    @Input
    var winMenuGroup = ""

    @Input
    var winShortcut = false

    @Input
    var winPerUserInstall = false

    // OS X specific parameters
    @Input
    var macPackageIdentifier = ""

    @Input
    var macPackageName = ""

    @Input
    var macPackageSigningPrefix = ""

    @Input
    var macSign = false

    @Input
    var macSigningKeychain = ""

    @Input
    var macSigningKeyUserName = ""

    // Linux specific parameters

    @Input
    var linuxPackageName = ""

    @Input
    var linuxDebMaintainer = ""

    @Input
    var linuxMenuGroup = ""

    @Input
    var linuxRpmLicenseType = ""

    @Input
    var linuxAppRelease = ""

    @Input
    var linuxAppCategory = ""

    @Input
    var linuxShortcut = false

    @TaskAction
    fun action() {
        var jpackage = getJPackageFromToolchain()
        if (jpackage == null) {
            jpackage = getJPackageFromJavaHome()
        }
        println("Using: $jpackage")

        execute(jpackage)
    }

    private fun buildExecutablePath(home: String): String {
        val executable = "$home${File.separator}bin${File.separator}$EXECUTABLE"
        return if (OperatingSystem.current().isWindows) {
            "${executable}.exe"
        } else {
            executable
        }
    }

    private fun getJPackageFromToolchain(): String? {
        logger.info("Looking for $EXECUTABLE in toolchain")
        try {
            val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
            val service = project.extensions.getByType(JavaToolchainService::class.java)
            val defaultLauncher = service.launcherFor(toolchain)
            val home = defaultLauncher.get().metadata.installationPath.asFile.absolutePath
            val executable = buildExecutablePath(home)
            if (File(executable).exists()) {
                return executable
            } else {
                logger.warn("File $executable does not exist")
                return null
            }
        } catch (ex: Exception) {
            logger.warn("Toolchain is not configured")
            return null
        }
    }

    private fun getJPackageFromJavaHome(): String {
        logger.info("Getting jpackage from java.home")
        val javaHome = System.getProperty("java.home") ?: throw GradleException("java.home is not set")
        return buildExecutablePath(javaHome)
    }

    private fun buildParameters(parameters: ArrayList<String>) {
        if (type != ImageType.DEFAULT) {
            parameters.addParameter("--type", type)
        }

        parameters.addParameter("--verbose", verbose)
            .addParameter("--name", appName)
            .addParameter("--app-version", appVersion)
            .addParameter("--dest", destination)
            .addParameter("--copyright", copyright)
            .addParameter("--description", appDescription)
            .addParameter("--runtime-image", runtimeImage)
            .addParameter("--input", input)
            .addParameter("--install-dir", installDir)
            .addParameter("--vendor", vendor)
            .addParameter("--module", module)
            .addParameter("--main-class", mainClass)
            .addParameter("--main-jar", mainJar)
            .addParameter("--module-path", modulePath)
            .addParameter("--icon", icon)

        for (option in javaOptions) {
            parameters.addParameter("--java-options", option.escape())
        }

        for (arg in arguments) {
            parameters.addParameter("--arguments", arg.escape())
        }

        if (OperatingSystem.current().isMacOsX) {
            parameters.addParameter("--mac-package-identifier", macPackageIdentifier)
                .addParameter("--mac-package-name", macPackageName)
                .addParameter("--mac-package-signing-prefix", macPackageSigningPrefix)
                .addParameter("--mac-sign", macSign)
                .addParameter("--mac-signing-keychain", macSigningKeychain)
                .addParameter("--mac-signing-key-user-name", macSigningKeyUserName)
        } else if (OperatingSystem.current().isWindows) {
            parameters.addParameter("--win-menu", winMenu)
                .addParameter("--win-dir-chooser", winDirChooser)
                .addParameter("--win-upgrade-uuid", winUpgradeUuid)
                .addParameter("--win-menu-group", winMenuGroup)
                .addParameter("--win-shortcut", winShortcut)
                .addParameter("--win-per-user-install", winPerUserInstall)
        } else if (OperatingSystem.current().isLinux) {
            parameters.addParameter("--linux-package-name", linuxPackageName)
                .addParameter("--linux-deb-maintainer", linuxDebMaintainer)
                .addParameter("--linux-menu-group", linuxMenuGroup)
                .addParameter("--linux-rpm-license-type", linuxRpmLicenseType)
                .addParameter("--linux-app-release", linuxAppRelease)
                .addParameter("--linux-app-category", linuxAppCategory)
                .addParameter("--linux-shortcut", linuxShortcut)
        }
    }

    private fun execute(cmd: String) {
        val processBuilder = ProcessBuilder()
        val parameters = ArrayList<String>()
        parameters.add(if (cmd.contains(" ")) "\"" + cmd + "\"" else cmd)
        buildParameters(parameters)
        processBuilder.command(parameters)
        val process = processBuilder.start()
        println("jpackage output:")
        logCmdOutput(process.inputStream)
        logCmdOutput(process.errorStream)
        val status = process.waitFor()
        if (status != 0) {
            throw GradleException("Error while executing $EXECUTABLE")
        }
    }

    private fun logCmdOutput(stream: InputStream) {
        BufferedReader(InputStreamReader(stream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println(line)
            }
        }
    }

    fun windows(block: () -> Unit) {
        if (OperatingSystem.current().isWindows) {
            block()
        }
    }

    fun mac(block: () -> Unit) {
        if (OperatingSystem.current().isMacOsX) {
            block()
        }
    }

    fun linux(block: () -> Unit) {
        if (OperatingSystem.current().isLinux) {
            block()
        }
    }
}
