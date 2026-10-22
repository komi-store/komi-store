package zed.rainxch.core.domain.model.installation

enum class InstallerType {
    DEFAULT,
    SHIZUKU,
    DHIZUKU,
    ROOT,
    THIRD_PARTY,
    ;

    companion object {
        fun fromName(name: String?): InstallerType = entries.find { it.name == name } ?: DEFAULT
    }
}
