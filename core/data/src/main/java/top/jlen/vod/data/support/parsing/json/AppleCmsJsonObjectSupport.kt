package top.jlen.vod.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

internal fun JsonObject.extractNoticeItems(): List<JsonElement> {
    val candidates = listOfNotNull(
        getAsJsonObject("data")?.getAsJsonArray("items"),
        getAsJsonArray("items"),
        getAsJsonObject("data")?.getAsJsonArray("list"),
        getAsJsonArray("list"),
        getAsJsonObject("data")?.getAsJsonArray("rows"),
        getAsJsonArray("rows")
    )
    return candidates.firstOrNull()?.toList().orEmpty()
}

internal fun JsonObject.firstString(vararg names: String): String =
    names.asSequence()
        .mapNotNull { name ->
            get(name)
                ?.takeIf { !it.isJsonNull }
                ?.let { value ->
                    runCatching {
                        when {
                            value.asJsonPrimitive.isString -> value.asString
                            value.asJsonPrimitive.isNumber -> value.asNumber.toString()
                            value.asJsonPrimitive.isBoolean -> value.asBoolean.toString()
                            else -> value.toString()
                        }
                    }.getOrNull()
                }
                ?.trim()
                ?.takeUnless {
                    it.equals("null", ignoreCase = true) ||
                        it.equals("undefined", ignoreCase = true) ||
                        it.equals("none", ignoreCase = true)
                }
                ?.takeIf(String::isNotBlank)
        }
        .firstOrNull()
        .orEmpty()

internal fun JsonObject.firstBoolean(vararg names: String): Boolean? =
    names.asSequence()
        .mapNotNull { name ->
            val value = get(name)?.takeIf { !it.isJsonNull } ?: return@mapNotNull null
            runCatching {
                val primitive = value.asJsonPrimitive
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> primitive.asInt != 0
                    primitive.isString -> {
                        when (primitive.asString.trim().lowercase(Locale.ROOT)) {
                            "1", "true", "yes", "on", "enabled", "active" -> true
                            "0", "false", "no", "off", "disabled", "inactive" -> false
                            else -> null
                        }
                    }
                    else -> null
                }
            }.getOrNull()
        }
        .firstOrNull()

internal fun JsonObject.firstInt(vararg names: String): Int? =
    names.asSequence()
        .mapNotNull { name ->
            val value = get(name)?.takeIf { !it.isJsonNull } ?: return@mapNotNull null
            runCatching {
                val primitive = value.asJsonPrimitive
                when {
                    primitive.isNumber -> primitive.asInt
                    primitive.isString -> primitive.asString.trim().toIntOrNull()
                    primitive.isBoolean -> if (primitive.asBoolean) 1 else 0
                    else -> null
                }
            }.getOrNull()
        }
        .firstOrNull()

internal fun JsonObject.firstObject(vararg names: String): JsonObject? =
    names.asSequence()
        .mapNotNull { name ->
            get(name)
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
        }
        .firstOrNull()

internal fun JsonObject.firstObjectOrFirstArrayObject(vararg names: String): JsonObject? =
    names.asSequence()
        .mapNotNull { name ->
            val value = get(name) ?: return@mapNotNull null
            when {
                value.isJsonObject -> value.asJsonObject
                value.isJsonArray -> value.asJsonArray.firstOrNull { it.isJsonObject }?.asJsonObject
                else -> null
            }
        }
        .firstOrNull()

internal fun JsonObject.firstArray(vararg names: String): List<JsonElement> =
    names.asSequence()
        .mapNotNull { name ->
            get(name)
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.toList()
        }
        .firstOrNull()
        .orEmpty()
