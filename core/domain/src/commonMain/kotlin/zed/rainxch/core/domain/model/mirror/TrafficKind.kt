package zed.rainxch.core.domain.model.mirror

enum class TrafficKind {
    RELEASE_ASSET,
    RAW_FILE,
    ;

    companion object {
        fun fromWire(value: String): TrafficKind? =
            when (value.lowercase()) {
                "release_asset" -> RELEASE_ASSET
                "raw_file" -> RAW_FILE
                else -> null
            }
    }
}
