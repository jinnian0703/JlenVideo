package top.jlen.vod

import android.app.Activity
import android.app.Application
import android.os.Bundle
import top.jlen.vod.data.AppleCmsRepository

class JlenVideoApplication : Application() {
    private var activeActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    activeActivityCount += 1
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (activity.isChangingConfigurations) return
                    activeActivityCount = (activeActivityCount - 1).coerceAtLeast(0)
                    if (activeActivityCount == 0) {
                        AppleCmsRepository.clearAllCaches(this@JlenVideoApplication)
                    }
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
        )
    }
}
