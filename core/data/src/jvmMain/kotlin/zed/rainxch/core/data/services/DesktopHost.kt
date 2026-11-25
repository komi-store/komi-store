package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.model.LinuxPackageType
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.system.SystemArchitecture
import java.io.File
import java.io.IOException

internal class DesktopHost(
    private val platform: Platform,
) {
    val isRunningInFlatpak: Boolean by lazy {
        File("/.flatpak-info").exists() || System.getenv("FLATPAK_ID") != null
    }

    val linuxPackageType: LinuxPackageType by lazy {
        when {
            platform != Platform.LINUX -> LinuxPackageType.UNIVERSAL
            isRunningInFlatpak -> flatpakHostPackageType()
            else -> nativePackageType()
        }
    }

    val systemArchitecture: SystemArchitecture by lazy {
        val machine = if (platform == Platform.MACOS) unameMachine() else null
        when (machine) {
            "arm64" -> SystemArchitecture.AARCH64
            "x86_64" -> SystemArchitecture.X86_64
            else -> System.getProperty("os.arch")?.let { SystemArchitecture.fromString(it) } ?: SystemArchitecture.UNKNOWN
        }
    }

    fun hasCommand(command: String): Boolean = processExitCode(listOf("which", command)).getOrNull() == 0

    private fun unameMachine(): String? =
        try {
            val process = ProcessBuilder("uname", "-m").start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            process.waitFor()
            output
        } catch (e: IOException) {
            Logger.d(e) { "uname -m failed; reading os.arch instead" }
            null
        } catch (e: InterruptedException) {
            Logger.d(e) { "uname -m was interrupted; reading os.arch instead" }
            null
        }

    private fun flatpakHostPackageType(): LinuxPackageType {
        val osRelease = readHostOsRelease() ?: return LinuxPackageType.UNIVERSAL
        Logger.d { "Host distro detected from Flatpak: ID=${osRelease.id}, ID_LIKE=${osRelease.idLike}" }
        val family = osRelease.family
        if (family == null) {
            Logger.d { "Could not classify host distro, defaulting to UNIVERSAL" }
        } else {
            Logger.d { "Host is ${family.label}: ${osRelease.id}" }
        }
        return family?.packageType ?: LinuxPackageType.UNIVERSAL
    }

    private fun readHostOsRelease(): OsRelease? {
        val hostOsRelease = File("/run/host/os-release")
        if (!hostOsRelease.exists()) {
            Logger.w { "Host os-release not available at /run/host/os-release" }
            return null
        }
        return try {
            OsRelease.parse(hostOsRelease.readText())
        } catch (e: IOException) {
            Logger.w(e) { "Failed to detect host Linux package type from Flatpak" }
            null
        }
    }

    private fun nativePackageType(): LinuxPackageType {
        val osRelease = readOsRelease() ?: return packageTypeFromPackageManager()
        val family = osRelease.family ?: return packageTypeFromPackageManager()
        Logger.d { "Detected ${family.label} distribution: ${osRelease.id}" }
        return family.packageType
    }

    private fun readOsRelease(): OsRelease? {
        for (filePath in listOf("/etc/os-release", "/usr/lib/os-release")) {
            try {
                val file = File(filePath)
                if (file.exists()) return OsRelease.parse(file.readText())
            } catch (e: IOException) {
                Logger.w(e) { "Could not read $filePath" }
            }
        }
        return null
    }

    private fun packageTypeFromPackageManager(): LinuxPackageType {
        val match = PACKAGE_MANAGERS.firstOrNull { (command, _) -> hasCommand(command) }
        if (match == null) {
            Logger.d { "Could not determine package type, defaulting to UNIVERSAL" }
        } else {
            Logger.d { "Detected package manager: ${match.first}" }
        }
        return match?.second ?: LinuxPackageType.UNIVERSAL
    }

    private class OsRelease(
        fields: Map<String, String>,
    ) {
        val id = fields["ID"]?.lowercase() ?: ""
        val idLike = fields["ID_LIKE"]?.lowercase() ?: ""
        val family = DistroFamily.entries.firstOrNull { it.matches(id, idLike) }

        companion object {
            fun parse(content: String): OsRelease =
                OsRelease(
                    content
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .map { it.split("=", limit = 2) }
                        .filter { it.size == 2 }
                        .associate { (key, value) -> key.trim() to value.trim().removeSurrounding("\"") },
                )
        }
    }

    private enum class DistroFamily(
        val packageType: LinuxPackageType,
        val label: String,
        private val ids: Set<String>,
        private val idLikeMarkers: List<String>,
    ) {
        DEBIAN(
            packageType = LinuxPackageType.DEB,
            label = "Debian-based",
            ids = setOf("debian", "ubuntu", "linuxmint", "pop", "elementary"),
            idLikeMarkers = listOf("debian", "ubuntu"),
        ),
        RPM(
            packageType = LinuxPackageType.RPM,
            label = "RPM-based",
            ids = setOf("fedora", "rhel", "centos", "rocky", "almalinux", "opensuse", "suse"),
            idLikeMarkers = listOf("fedora", "rhel", "suse", "centos"),
        ),
        ARCH(
            packageType = LinuxPackageType.ARCH,
            label = "Arch-based",
            ids = setOf("arch", "manjaro", "endeavouros", "artix", "cachyos", "garuda", "arcolinux", "parabola"),
            idLikeMarkers = listOf("arch"),
        ),
        ;

        fun matches(
            id: String,
            idLike: String,
        ): Boolean = id in ids || idLikeMarkers.any { idLike.contains(it) }
    }

    private companion object {
        val PACKAGE_MANAGERS =
            listOf(
                "apt" to LinuxPackageType.DEB,
                "apt-get" to LinuxPackageType.DEB,
                "dnf" to LinuxPackageType.RPM,
                "yum" to LinuxPackageType.RPM,
                "zypper" to LinuxPackageType.RPM,
                "pacman" to LinuxPackageType.ARCH,
            )
    }
}
