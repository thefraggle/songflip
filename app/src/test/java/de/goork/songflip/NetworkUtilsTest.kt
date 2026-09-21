package de.goork.songflip

import android.content.Context
import de.goork.songflip.data.NetworkUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun testFallbackWhenConnectivityManagerUnavailable() {
        // Test context returning null for system service fallback
        val mockContext = object : Context() {
            override fun getSystemService(name: String): Any? = null
            override fun getAssets(): android.content.res.AssetManager = throw UnsupportedOperationException()
            override fun getResources(): android.content.res.Resources = throw UnsupportedOperationException()
            override fun getPackageManager(): android.content.pm.PackageManager = throw UnsupportedOperationException()
            override fun getContentResolver(): android.content.ContentResolver = throw UnsupportedOperationException()
            override fun getMainLooper(): android.os.Looper = throw UnsupportedOperationException()
            override fun getApplicationContext(): Context = this
            override fun setTheme(resid: Int) {}
            override fun getTheme(): android.content.res.Resources.Theme = throw UnsupportedOperationException()
            override fun getClassLoader(): ClassLoader = javaClass.classLoader
            override fun getPackageName(): String = "de.goork.songflip"
            override fun getApplicationInfo(): android.content.pm.ApplicationInfo = throw UnsupportedOperationException()
            override fun getPackageResourcePath(): String = ""
            override fun getPackageCodePath(): String = ""
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = throw UnsupportedOperationException()
            override fun moveSharedPreferencesFrom(sourceContext: Context?, name: String?): Boolean = false
            override fun deleteSharedPreferences(name: String?): Boolean = false
            override fun openFileInput(name: String?): java.io.FileInputStream = throw UnsupportedOperationException()
            override fun openFileOutput(name: String?, mode: Int): java.io.FileOutputStream = throw UnsupportedOperationException()
            override fun deleteFile(name: String?): Boolean = false
            override fun getFileStreamPath(name: String?): java.io.File = throw UnsupportedOperationException()
            override fun fileList(): Array<String> = emptyArray()
            override fun getFilesDir(): java.io.File = java.io.File(".")
            override fun getNoBackupFilesDir(): java.io.File = java.io.File(".")
            override fun getExternalFilesDir(type: String?): java.io.File? = null
            override fun getExternalFilesDirs(type: String?): Array<java.io.File> = emptyArray()
            override fun getObbDir(): java.io.File = java.io.File(".")
            override fun getObbDirs(): Array<java.io.File> = emptyArray()
            override fun getCacheDir(): java.io.File = java.io.File(".")
            override fun getCodeCacheDir(): java.io.File = java.io.File(".")
            override fun getExternalCacheDir(): java.io.File? = null
            override fun getExternalCacheDirs(): Array<java.io.File> = emptyArray()
            override fun getExternalMediaDirs(): Array<java.io.File> = emptyArray()
            override fun getDataDir(): java.io.File = java.io.File(".")
            override fun getDir(name: String?, mode: Int): java.io.File = java.io.File(".")
            override fun openOrCreateDatabase(name: String?, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
            override fun openOrCreateDatabase(name: String?, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
            override fun moveDatabaseFrom(sourceContext: Context?, name: String?): Boolean = false
            override fun deleteDatabase(name: String?): Boolean = false
            override fun getDatabasePath(name: String?): java.io.File = throw UnsupportedOperationException()
            override fun databaseList(): Array<String> = emptyArray()
            @Deprecated("Deprecated in Java")
            override fun getWallpaper(): android.graphics.drawable.Drawable = throw UnsupportedOperationException()
            @Deprecated("Deprecated in Java")
            override fun peekWallpaper(): android.graphics.drawable.Drawable = throw UnsupportedOperationException()
            @Deprecated("Deprecated in Java")
            override fun getWallpaperDesiredMinimumWidth(): Int = 0
            @Deprecated("Deprecated in Java")
            override fun getWallpaperDesiredMinimumHeight(): Int = 0
            @Deprecated("Deprecated in Java")
            override fun setWallpaper(bitmap: android.graphics.Bitmap?) {}
            @Deprecated("Deprecated in Java")
            override fun setWallpaper(data: java.io.InputStream?) {}
            @Deprecated("Deprecated in Java")
            override fun clearWallpaper() {}
            override fun startActivity(intent: android.content.Intent?) {}
            override fun startActivity(intent: android.content.Intent?, options: android.os.Bundle?) {}
            override fun startActivities(intents: Array<out android.content.Intent>?) {}
            override fun startActivities(intents: Array<out android.content.Intent>?, options: android.os.Bundle?) {}
            override fun startIntentSender(intent: android.content.IntentSender?, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int) {}
            override fun startIntentSender(intent: android.content.IntentSender?, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int, options: android.os.Bundle?) {}
            override fun sendBroadcast(intent: android.content.Intent?) {}
            override fun sendBroadcast(intent: android.content.Intent?, receiverPermission: String?) {}
            override fun sendOrderedBroadcast(intent: android.content.Intent?, receiverPermission: String?) {}
            override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
            override fun sendBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?) {}
            override fun sendBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?, receiverPermission: String?) {}
            override fun sendOrderedBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
            override fun sendStickyBroadcast(intent: android.content.Intent?) {}
            override fun sendStickyOrderedBroadcast(intent: android.content.Intent?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
            override fun removeStickyBroadcast(intent: android.content.Intent?) {}
            override fun sendStickyBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?) {}
            override fun sendStickyOrderedBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) {}
            override fun removeStickyBroadcastAsUser(intent: android.content.Intent?, user: android.os.UserHandle?) {}
            override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?): android.content.Intent? = null
            override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, flags: Int): android.content.Intent? = null
            override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, broadcastPermission: String?, scheduler: android.os.Handler?): android.content.Intent? = null
            override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, broadcastPermission: String?, scheduler: android.os.Handler?, flags: Int): android.content.Intent? = null
            override fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) {}
            override fun startService(service: android.content.Intent?): android.content.ComponentName? = null
            override fun startForegroundService(service: android.content.Intent?): android.content.ComponentName? = null
            override fun stopService(service: android.content.Intent?): Boolean = false
            override fun bindService(service: android.content.Intent, conn: android.content.ServiceConnection, flags: Int): Boolean = false
            override fun unbindService(conn: android.content.ServiceConnection) {}
            override fun startInstrumentation(className: android.content.ComponentName, profileFile: String?, arguments: android.os.Bundle?): Boolean = false
            override fun getSystemServiceName(serviceClass: Class<*>): String? = null
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int = 0
            override fun checkCallingPermission(permission: String): Int = 0
            override fun checkCallingOrSelfPermission(permission: String): Int = 0
            override fun checkSelfPermission(permission: String): Int = 0
            override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) {}
            override fun enforceCallingPermission(permission: String, message: String?) {}
            override fun enforceCallingOrSelfPermission(permission: String, message: String?) {}
            override fun grantUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) {}
            override fun revokeUriPermission(uri: android.net.Uri?, modeFlags: Int) {}
            override fun revokeUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) {}
            override fun checkUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int): Int = 0
            override fun checkCallingUriPermission(uri: android.net.Uri?, modeFlags: Int): Int = 0
            override fun checkCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int): Int = 0
            override fun checkUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int): Int = 0
            override fun enforceUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int, message: String?) {}
            override fun enforceCallingUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) {}
            override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) {}
            override fun enforceUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?) {}
            override fun createPackageContext(packageName: String?, flags: Int): Context = throw UnsupportedOperationException()
            override fun createContextForSplit(splitName: String?): Context = throw UnsupportedOperationException()
            override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration): Context = throw UnsupportedOperationException()
            override fun createDisplayContext(display: android.view.Display): Context = throw UnsupportedOperationException()
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun isDeviceProtectedStorage(): Boolean = false
        }

        // When ConnectivityManager is unavailable, it should safely return true (fail-open)
        assertTrue(NetworkUtils.isNetworkAvailable(mockContext))
    }
}
