# Keep runtime metadata used by Retrofit and Gson. Release builds run R8, but
# network/cache models are populated by reflection and must keep their fields.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep class top.jlen.vod.data.** { *; }
-keep class top.jlen.vod.ui.**CacheSnapshot { *; }
-keep class top.jlen.vod.ui.**HistoryCacheSnapshot { *; }

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
