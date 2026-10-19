package zed.rainxch.core.domain.model.installation

import kotlinx.serialization.Serializable

@Serializable
enum class PresetKey(
    val packageName: String,
) {
    PLAY_STORE("com.android.vending"),
    FDROID("org.fdroid.fdroid"),
    OBTAINIUM("dev.imranr.obtainium.app"),
    ;

    companion object {
        fun fromName(name: String?): PresetKey? = entries.find { it.name == name }
    }
}
