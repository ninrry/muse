package luzzr.muse.core.log

import android.util.Log
import timber.log.Timber

/**
 * Centralised logger for Muse. Wraps [Timber] so that:
 *
 * - In debug builds every log is emitted (see [DebugTree]).
 * - In release builds logs are stripped at the Timber boundary (no-op tree).
 *
 * Use [MuseLog.d]/[i]/[w]/[e] instead of `Timber` directly so callers don't
 * need to know which tree is installed.
 */
object MuseLog {

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).d(message) else Timber.tag(tag).d(throwable, message)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).i(message) else Timber.tag(tag).i(throwable, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).w(message) else Timber.tag(tag).w(throwable, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).e(message) else Timber.tag(tag).e(throwable, message)
    }
}

/**
 * Debug tree that prepends the call-site class name. Useful when hunting down
 * the source of a noisy log line.
 */
class MuseDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        val className = element.className.substringAfterLast('.')
        return "$className.${element.methodName}:${element.lineNumber}"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Defensive: the default DebugTree already delegates to android.util.Log;
        // we route through it so that any custom log shipper can hook in here.
        if (priority == Log.VERBOSE || priority == Log.DEBUG) return
        super.log(priority, tag, message, t)
    }
}

/** No-op tree used in release to keep logcat silent while preserving call sites. */
class MuseReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // intentionally empty: release builds do not log to logcat.
    }
}
