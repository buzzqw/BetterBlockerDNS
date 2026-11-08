-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn sun.net.spi.nameservice.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-keep class lombok.** { *; }
-dontwarn lombok.**
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**
-keepattributes Signature
-keepclassmembers class * {
    @java.lang.Override *;
}
-keep class dagger.** { *; }
-keep class **$$HiltComponents** { *; }
-keep class **_Hilt { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
}
-dontwarn dagger.internal.codegen.**
# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
 -keep class com.buzzqw.bbdns.service.vpn.** { *; }
 -dontwarn java.nio.**
 -dontwarn java.nio.channels.**
 -dontwarn com.buzzqw.bbdns.**
-keep class java.nio.** { *; }
-dontwarn java.nio.**

# Fix InetAddressResolverProvider service warning
-dontwarn java.net.spi.InetAddressResolverProvider
-dontwarn java.net.spi.**
-dontnote java.net.spi.**

# Keep service files and providers
-keepclassmembers class * {
    *** loadService(***);
}

# Suppress kotlinx-serialization R8 warnings
-dontwarn kotlinx.serialization.**

# Keep license verification classes (prevent R8 from breaking premium code verification)
-keep class com.buzzqw.bbdns.data.remote.LicenseResponse { *; }
-keep class com.buzzqw.bbdns.data.remote.LicenseData { *; }
-keep class com.buzzqw.bbdns.data.remote.ActivationData { *; }
-keep class com.buzzqw.bbdns.data.remote.LicenseApi { *; }
-keep class com.buzzqw.bbdns.data.remote.LicenseRepository { *; }
-keep class com.buzzqw.bbdns.data.remote.LicenseRepositoryImpl { *; }
-keep class com.buzzqw.bbdns.data.remote.VerifyLicenseUseCase { *; }

# Room Database rules - prevent R8 from breaking database operations
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keepclassmembers @androidx.room.Entity class * {
    *;
}
-keepclassmembers @androidx.room.Dao class * {
    *;
}
-keep class * extends androidx.room.migration.Migration {
    *;
}
-keep class com.buzzqw.bbdns.data.database.** { *; }
-keep class com.buzzqw.bbdns.data.local.database.** { *; }
-keep class com.buzzqw.bbdns.domain.model.** { *; }

# Shizuku rules - prevent R8 from breaking Shizuku services and AIDL interfaces
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keepclassmembers class * implements rikka.shizuku.** {
    *;
}

# AIDL interface preservation
-keep class * extends android.os.Binder { *; }
-keep class * implements android.os.IInterface { *; }
-keep class **$Stub { *; }
-keep class **$Stub$Proxy { *; }

# Shizuku service classes
-keep class com.buzzqw.bbdns.service.shizuku.** { *; }
-keep interface com.buzzqw.bbdns.service.shizuku.** { *; }
-keep class com.buzzqw.bbdns.di.firewall.** { *; }

# AIDL generated classes and interfaces
-keep class com.buzzqw.bbdns.service.shizuku.IShizukuFirewallService { *; }
-keep class com.buzzqw.bbdns.service.shizuku.IShizukuFirewallService$Stub { *; }
-keep class com.buzzqw.bbdns.service.shizuku.IShizukuFirewallService$Stub$Proxy { *; }

# Preserve Shizuku UserService args and binding
-keepclassmembers class rikka.shizuku.Shizuku$UserServiceArgs {
    *;
}
-keep class android.content.ServiceConnection { *; }
-keepclassmembers class * implements android.content.ServiceConnection {
    *;
}

# Preserve reflection-based method calls used by Shizuku
-keepclassmembers class * {
    @rikka.shizuku.** *;
}

# Keep Shizuku permission and version checking methods
-keepclassmembers class rikka.shizuku.Shizuku {
    public static boolean pingBinder();
    public static int checkSelfPermission();
    public static void requestPermission(int);
    public static int getVersion();
}

# Enhanced optimization rules for smaller APK size
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
