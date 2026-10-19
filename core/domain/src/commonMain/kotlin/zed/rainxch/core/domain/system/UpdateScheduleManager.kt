package zed.rainxch.core.domain.system

interface UpdateScheduleManager {
    fun reschedule(intervalHours: Long)

    fun cancel()
}
