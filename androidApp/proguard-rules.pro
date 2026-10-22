# ============================================================================
# ProGuard / R8 Rules for GitHub Store (KMP + Compose Multiplatform)
# ============================================================================
# Used with: proguard-android-optimize.txt (enables optimization passes)
# ============================================================================

# ── General Attributes ──────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions

# ── Kotlin Core ─────────────────────────────────────────────────────────────
# Keep Kotlin metadata for reflection used by serialization & Koin
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Kotlin Coroutines ──────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Kotlinx Serialization ──────────────────────────────────────────────────
# Serialization engine internals
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontnote kotlinx.serialization.**

# Generated serializers for ALL @Serializable classes
-keep class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# App @Serializable classes (DTOs, models, navigation routes) across all packages
-keep @kotlinx.serialization.Serializable class zed.rainxch.** { *; }
-keep,includedescriptorclasses class zed.rainxch.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class zed.rainxch.** {
    *** Companion;
}

# ── Navigation Routes ──────────────────────────────────────────────────────
# Type-safe navigation requires these classes to survive R8
-keep class zed.rainxch.githubstore.app.navigation.GithubStoreGraph { *; }
-keep class zed.rainxch.githubstore.app.navigation.GithubStoreGraph$* { *; }

# ── Network DTOs – Core Module ─────────────────────────────────────────────
-keep class zed.rainxch.core.data.dto.** { *; }

# ── Network DTOs – Feature Modules ─────────────────────────────────────────
-keep class zed.rainxch.search.data.dto.** { *; }
-keep class zed.rainxch.devprofile.data.dto.** { *; }
-keep class zed.rainxch.home.data.dto.** { *; }

# ── Domain Models ──────────────────────────────────────────────────────────
-keep class zed.rainxch.core.domain.model.account.github.GithubRepoSummary { *; }
-keep class zed.rainxch.core.domain.model.account.github.GithubUser { *; }

# Keep enums used by Room TypeConverters and serialization
-keep class zed.rainxch.core.domain.model.InstallSource { *; }
-keep class zed.rainxch.core.domain.model.AppTheme { *; }
-keep class zed.rainxch.core.domain.model.FontTheme { *; }
-keep class zed.rainxch.core.domain.model.Platform { *; }
-keep class zed.rainxch.core.domain.model.SystemArchitecture { *; }
-keep class zed.rainxch.core.domain.model.PackageChangeType { *; }

# ── Room Database ──────────────────────────────────────────────────────────
# Database class and generated implementation
-keep class zed.rainxch.core.data.local.db.AppDatabase { *; }
-keep class zed.rainxch.core.data.local.db.AppDatabase_Impl { *; }

# Entities
-keep class zed.rainxch.core.data.local.db.entities.** { *; }

# DAOs
-keep interface zed.rainxch.core.data.local.db.dao.** { *; }
-keep class zed.rainxch.core.data.local.db.dao.** { *; }

# Room runtime
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# ── Ktor ───────────────────────────────────────────────────────────────────
# Engine discovery, plugin system, and content negotiation use reflection
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.plugins.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.utils.io.** { *; }
-keep class io.ktor.http.** { *; }
-keepnames class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn java.lang.management.**

# ── OkHttp (Ktor engine) ──────────────────────────────────────────────────
-keep class okhttp3.internal.platform.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Okio ───────────────────────────────────────────────────────────────────
-dontwarn okio.**

# ── SSL/TLS ────────────────────────────────────────────────────────────────
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# ── Koin DI ────────────────────────────────────────────────────────────────
# Koin uses reflection for constructor injection
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }
-dontwarn org.koin.**

# Keep ViewModels so Koin can instantiate them
-keep class zed.rainxch.**.presentation.**ViewModel { *; }
-keep class zed.rainxch.**.presentation.**ViewModel$* { *; }

# ── Compose / AndroidX ────────────────────────────────────────────────────
# Compose runtime and navigation (most rules come bundled with the library)
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# ── DataStore ──────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.**

# ── Landscapist / Coil3 (Image Loading) ────────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Multiplatform Markdown Renderer ────────────────────────────────────────
-keep class com.mikepenz.markdown.** { *; }
-keep class org.intellij.markdown.** { *; }
-dontwarn com.mikepenz.markdown.**
-dontwarn org.intellij.markdown.**

# ── Kermit Logging ─────────────────────────────────────────────────────────
-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ── MOKO Permissions ──────────────────────────────────────────────────────
-keep class dev.icerock.moko.permissions.** { *; }
-dontwarn dev.icerock.moko.**

# ── BuildKonfig (Generated Build Constants) ────────────────────────────────
-keep class zed.rainxch.githubstore.BuildConfig { *; }
-keep class zed.rainxch.**.BuildKonfig { *; }
-keep class **.BuildKonfig { *; }

# ── AndroidX Security / Crypto ─────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# ── Firebase (if integrated) ──────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Shizuku (Silent Install) ─────────────────────────────────────────────
# The UserService class name is passed to Shizuku via ComponentName — R8 must
# not rename or remove it, otherwise bindUserService() silently fails.
-keep class zed.rainxch.core.data.services.shizuku.ShizukuInstallerServiceImpl { *; }

# AIDL-generated Stub/Proxy classes for IPC between app and privileged process
-keep class zed.rainxch.core.data.services.shizuku.IShizukuInstallerService { *; }
-keep class zed.rainxch.core.data.services.shizuku.IShizukuInstallerService$Stub { *; }
-keep class zed.rainxch.core.data.services.shizuku.IShizukuInstallerService$Stub$Proxy { *; }

# Shizuku library internals (binder listeners, service args, provider)
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ── Enum safety ────────────────────────────────────────────────────────────
# Keep all enum values and valueOf methods (used by serialization/Room)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ─────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Java Serializable Compatibility ───────────────────────────────────────
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Suppress Warnings for Missing Classes ──────────────────────────────────
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
