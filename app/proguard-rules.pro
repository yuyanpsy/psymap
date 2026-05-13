# ============================================================
# PsyMap ProGuard Rules
# ============================================================

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# 保留自己的代码（数据类需要字段名给 Gson 用）
-keep class com.psymap.app.** { *; }

# ============================================================
# Gson
# ============================================================
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================================
# OkHttp + Okio
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# Room
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ============================================================
# Google ML Kit
# ============================================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============================================================
# Apache POI
# ============================================================
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }

# ============================================================
# PDFBox
# ============================================================
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# ============================================================
# Log4j / SLF4J
# ============================================================
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**

# ============================================================
# 微信 SDK
# ============================================================
-keep class com.tencent.mm.opensdk.** { *; }
-keep class com.tencent.wxop.** { *; }

# ============================================================
# BouncyCastle (POI 加密依赖)
# ============================================================
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }

# ============================================================
# 通用
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.misc.**
-dontwarn com.sun.**
