package zed.rainxch.core.domain.utils

suspend fun executeInTransaction(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        throw e
    }
}
