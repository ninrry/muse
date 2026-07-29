package luzzr.muse.core.log

enum class MuseLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

fun interface MuseLogSink {
    fun log(level: MuseLogLevel, tag: String, message: String, throwable: Throwable?)
}

object MuseLog {
    @Volatile
    private var sink: MuseLogSink? = null

    fun install(sink: MuseLogSink) {
        this.sink = sink
    }

    fun clear() {
        sink = null
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        sink?.log(MuseLogLevel.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        sink?.log(MuseLogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        sink?.log(MuseLogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        sink?.log(MuseLogLevel.ERROR, tag, message, throwable)
    }
}
