package luzzr.muse

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class MuseTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(classLoader, Application::class.java.name, context)
    }
}
