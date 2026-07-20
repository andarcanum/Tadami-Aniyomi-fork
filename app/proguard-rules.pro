-dontobfuscate

-keep,allowoptimization class eu.kanade.**
-keep,allowoptimization class tachiyomi.**
-keep,allowoptimization class mihon.**

# Keep common dependencies used in extensions
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class android.content.** { *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class android.test.base.** { *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep class app.cash.quickjs.** { *; }

# QuickJS native bridge: NativeApi methods are bound reflectively via QuickJs.set(),
# so R8 sees no direct Kotlin callers of the bridge interface methods. Keep the
# whole novel runtime package unoptimized so the bridge interface, its
# implementations and the generated JS shim stay in sync (method names are the
# JS contract), and nothing gets inlined away or merged incorrectly.
-keep class eu.kanade.tachiyomi.extension.novel.runtime.** { *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
-keep,allowoptimization class com.arthenica.** { public protected *; }

# WorkManager/Room use reflection for the generated database implementation in release builds.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Extension loading is a dynamic ClassLoader seam. Keep loaders non-optimized so
# release builds don't diverge from debug through R8 lambda merging/specialization
# around reflective source instantiation and fallback class loaders.
-keep class eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader { *; }
-keep class eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader$* { *; }
-keep class eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader { *; }
-keep class eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader$* { *; }
-keep class eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionSupport { *; }
-keep class eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionSupport$* { *; }
-keep class eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader { *; }

# Novel Kotlin extensions are dynamically loaded and cast to host-side source APIs.
# Keep the extension-facing API stable in release/minified builds.
-keep class eu.kanade.tachiyomi.novelsource.** { *; }
-keep class eu.kanade.tachiyomi.source.novel.** { *; }

# From extensions-lib
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }

# Optional runtime APIs that are referenced by transitive libraries but are not
# required on Android runtime targets we ship.
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.graalvm.nativeimage.hosted.**
-dontwarn org.jspecify.annotations.NullMarked
-dontwarn java.lang.Module

# Google OAuth models are populated from JSON via @Key reflection.
# In release builds, R8 was removing constructors/fields used by the
# Google Drive auth flow, which makes sign-in fail before the browser opens.
-keep class com.google.api.client.auth.oauth2.TokenResponse { *; }
-keep class com.google.api.client.auth.oauth2.TokenErrorResponse { *; }
-keep class com.google.api.client.auth.oauth2.TokenRequest { *; }
-keep class com.google.api.client.auth.oauth2.AuthorizationRequestUrl { *; }
-keep class com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl { *; }
-keep class com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest { *; }
-keep class com.google.api.client.auth.oauth2.RefreshTokenRequest { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets$Details { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest { *; }

# Google Drive sync also builds request/query payloads and file metadata via
# @Key-backed reflection. Release shrinking was stripping request/model fields
# used by appDataFolder list/get/create/update calls, which can surface as
# generic "key error" sync failures after sign-in succeeds.
-keep class com.google.api.services.drive.model.File { *; }
-keep class com.google.api.services.drive.model.FileList { *; }
-keep class com.google.api.services.drive.Drive$Files$Get { *; }
-keep class com.google.api.services.drive.Drive$Files$List { *; }
-keep class com.google.api.services.drive.Drive$Files$Create { *; }
-keep class com.google.api.services.drive.Drive$Files$Update { *; }

# Google API client models are populated through reflection on @Key fields.
# In release builds, R8 can strip members or whole classes that are only
# referenced reflectively, which breaks OAuth, Drive metadata parsing, and
# sync error decoding.
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.auth.** { *; }
-keep class com.google.api.services.drive.** { *; }

# Jackson2 backend used by google-http-client-jackson2.
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**

# Apache HTTP transitives referenced from Google client internals can mention
# desktop/JVM-only JNDI and GSS APIs that do not exist on Android and are not
# used by our Google Drive appDataFolder flow.
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }

# Secret Hall — loaded via Class.forName, prevent R8 merge/optimize in AGP 9.x
-keep class eu.kanade.presentation.browse.local.SecretHallGateImpl { *; }
-keep class eu.kanade.presentation.browse.local.SecretHallOfFameScreen { *; }
-keep class eu.kanade.presentation.browse.local.SecretHallSceneConfig { *; }
-keep class eu.kanade.presentation.browse.SecretHallGate { *; }

# Novel reader release stability
# Reader settings and source overrides are persisted and may be restored through
# preference/serialization layers. Keep these models/enums non-optimized so R8
# cannot enum-unbox/merge/specialize them in a way that changes nullable runtime
# behavior in release builds.
-keep class eu.kanade.tachiyomi.ui.reader.novel.setting.** { *; }
-keepclassmembers enum eu.kanade.tachiyomi.ui.reader.novel.setting.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# The novel reader screen contains Compose state machines for page reader,
# page-curl renderer, WebView, TTS, translations, and chapter handoff. Keep it
# non-optimized in release to avoid R8 lambda/enum switch specialization around
# nullable preference-derived enum values.
-keep class eu.kanade.presentation.reader.novel.NovelReaderScreenKt { *; }
-keep class eu.kanade.presentation.reader.novel.NovelReaderScreenKt$* { *; }
-keep class eu.kanade.presentation.reader.novel.PageTurnPageRendererKt { *; }
-keep class eu.kanade.presentation.reader.novel.PageTurnPageRendererKt$* { *; }
-keep class eu.kanade.presentation.reader.novel.NovelReaderSystemUiPolicyKt { *; }
-keep class eu.kanade.presentation.reader.novel.NovelReaderSystemUiPolicyKt$* { *; }

# TorrServer / torrent streaming
# Keep public torrent extension API and core TorrServer models stable for
# extension calls, kotlinx.serialization, and native TorrServer bridge usage.
-keep,allowoptimization class eu.kanade.tachiyomi.torrentutils.** { public protected *; }
-keep,allowoptimization class aniyomi.core.common.torrent.** { public protected *; }
-keep,allowoptimization class aniyomi.core.common.torrent.model.** { public protected *; }
-keepclassmembers class aniyomi.core.common.torrent.model.** {
    *** Companion;
}
-keepclasseswithmembers class aniyomi.core.common.torrent.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn xyz.secozzi.torrserver.**
