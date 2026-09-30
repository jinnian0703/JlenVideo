package top.jlen.vod.data

import android.content.SharedPreferences

/** JVM 测试使用的内存存储，保留 SharedPreferences 的类型检查和原子编辑语义。 */
internal class InMemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = synchronized(values) { values.toMutableMap() }
    override fun contains(key: String): Boolean = synchronized(values) { values.containsKey(key) }
    override fun getString(key: String, defValue: String?): String? = synchronized(values) {
        (values[key] ?: defValue) as String?
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(values) { ((values[key] ?: defValues) as Set<String>?)?.toMutableSet() }

    override fun getInt(key: String, defValue: Int): Int = synchronized(values) { (values[key] ?: defValue) as Int }
    override fun getLong(key: String, defValue: Long): Long = synchronized(values) { (values[key] ?: defValue) as Long }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(values) { (values[key] ?: defValue) as Float }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(values) { (values[key] ?: defValue) as Boolean }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(values) {
            if (clearAll) values.clear()
            changes.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            true
        }
    }
}
