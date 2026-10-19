package zed.rainxch.repopages.domain.model

enum class AdvisorySeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
    ;

    companion object {
        fun fromApi(value: String?): AdvisorySeverity =
            when (value?.lowercase()) {
                "critical" -> CRITICAL
                "high" -> HIGH
                "medium" -> MEDIUM
                "low" -> LOW
                else -> UNKNOWN
            }
    }
}
