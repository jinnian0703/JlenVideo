package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

internal fun prefersSystemCast(): Boolean {
    val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return SYSTEM_CAST_VENDOR_KEYWORDS.any(vendor::contains)
}

internal fun launchSystemCast(context: Context): Boolean {
    val intents = buildList {
        if (isOplusDevice()) {
            add(
                Intent().setClassName(
                    OPLUS_CAST_PACKAGE,
                    OPLUS_CAST_DEVICE_LIST_ACTIVITY
                )
            )
            add(
                Intent(OPLUS_CAST_DEVICE_LIST_ACTION)
                    .setPackage(OPLUS_CAST_PACKAGE)
            )
        }

        add(Intent(Settings.ACTION_CAST_SETTINGS))
        add(Intent(ACTION_WIFI_DISPLAY_SETTINGS))

        if (isXiaomiDevice()) {
            add(
                Intent().setClassName(
                    ANDROID_SETTINGS_PACKAGE,
                    XIAOMI_CAST_SETTINGS_ACTIVITY
                )
            )
            add(
                Intent().setClassName(
                    ANDROID_SETTINGS_PACKAGE,
                    XIAOMI_WIFI_DISPLAY_SETTINGS_ACTIVITY
                )
            )
        }

        add(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    return intents.any { intent ->
        runCatching {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.isSuccess
    }
}

private fun isOplusDevice(): Boolean {
    val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return OPLUS_VENDOR_KEYWORDS.any(vendor::contains)
}

private fun isXiaomiDevice(): Boolean {
    val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return XIAOMI_VENDOR_KEYWORDS.any(vendor::contains)
}

private val SYSTEM_CAST_VENDOR_KEYWORDS = listOf(
    "oppo",
    "oplus",
    "realme",
    "oneplus",
    "xiaomi",
    "redmi",
    "poco",
    "huawei",
    "honor",
    "vivo",
    "iqoo",
    "samsung",
    "meizu",
    "zte",
    "nubia",
    "lenovo",
    "motorola"
)

private val OPLUS_VENDOR_KEYWORDS = listOf("oppo", "oplus", "realme", "oneplus")
private val XIAOMI_VENDOR_KEYWORDS = listOf("xiaomi", "redmi", "poco")

private const val OPLUS_CAST_PACKAGE = "com.oplus.cast"
private const val OPLUS_CAST_DEVICE_LIST_ACTIVITY = "com.oplus.cast.ui.DeviceListActivity"
private const val OPLUS_CAST_DEVICE_LIST_ACTION = "oplus.intent.action.cast.device.display"
private const val ACTION_WIFI_DISPLAY_SETTINGS = "android.settings.WIFI_DISPLAY_SETTINGS"
private const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"
private const val XIAOMI_CAST_SETTINGS_ACTIVITY = "com.android.settings.Settings\$CastSettingsActivity"
private const val XIAOMI_WIFI_DISPLAY_SETTINGS_ACTIVITY =
    "com.android.settings.Settings\$WifiDisplaySettingsActivity"
