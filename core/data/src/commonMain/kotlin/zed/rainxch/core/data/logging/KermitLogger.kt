package zed.rainxch.core.data.logging

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.logging.KomiStoreLogger

object KermitLogger : KomiStoreLogger {
    override fun debug(message: String) {
        Logger.d(message)
    }

    override fun info(message: String) {
        Logger.i(message)
    }

    override fun warn(message: String) {
        Logger.w(message)
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        Logger.e(message, throwable)
    }

    override fun withTag(tag: String): KomiStoreLogger = TaggedKermitLogger(Logger.withTag(tag))
}

private class TaggedKermitLogger(
    private val delegate: Logger,
) : KomiStoreLogger {
    override fun debug(message: String) {
        delegate.d(message)
    }

    override fun info(message: String) {
        delegate.i(message)
    }

    override fun warn(message: String) {
        delegate.w(message)
    }

    override fun error(
        message: String,
        throwable: Throwable?,
    ) {
        delegate.e(message, throwable)
    }

    override fun withTag(tag: String): KomiStoreLogger = TaggedKermitLogger(delegate.withTag(tag))
}
