# Keep runtime metadata used by Retrofit and Gson. Release builds run R8, but
# network/cache models are populated by reflection and must keep their fields.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep class top.jlen.vod.data.** { *; }
-keep class top.jlen.vod.ui.**CacheSnapshot { *; }
-keep class top.jlen.vod.ui.**HistoryCacheSnapshot { *; }
# 追剧缓存中的元素也由 Gson 反射读写，不能只保留外层快照。
-keep class top.jlen.vod.ui.FollowUpItem { *; }
# 保留匿名 TypeToken 的泛型签名，兼容 R8 full mode。
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
