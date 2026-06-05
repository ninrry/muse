package luzzr.muse.core.log

import android.util.Log
import timber.log.Timber

object TimberMuseLogSink : MuseLogSink {
    override fun log(level: MuseLogLevel, tag: String, message: String, throwable: Throwable?) {
        val tree = Timber.tag(tag)
        when (level) {
            MuseLogLevel.DEBUG -> if (throwable == null) tree.d(message) else tree.d(throwable, message)
            MuseLogLevel.INFO -> if (throwable == null) tree.i(message) else tree.i(throwable, message)
            MuseLogLevel.WARN -> if (throwable == null) tree.w(message) else tree.w(throwable, message)
            MuseLogLevel.ERROR -> if (throwable == null) tree.e(message) else tree.e(throwable, message)
        }
    }
}

class MuseDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        val className = element.className.substringAfterLast('.')
        return "$className.${element.methodName}:${element.lineNumber}"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.VERBOSE || priority == Log.DEBUG) return
        super.log(priority, tag, message, t)
    }
}

class MuseReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit
}
