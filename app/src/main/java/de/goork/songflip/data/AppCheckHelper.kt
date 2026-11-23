package de.goork.songflip.data

import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import de.goork.songflip.BuildConfig
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Programmatic Firebase App Check (Play Integrity) helper (#295, Phase 1 soft rollout).
 *
 * Initializes FirebaseApp and the Play Integrity App Check provider only when all three
 * Firebase parameters are supplied via local.properties or CI secrets, keeping open-source
 * clones completely free of google-services.json and Google Services Gradle plugins.
 * Token failures or timeouts degrade gracefully to `null` so soft monitoring never blocks
 * legitimate recognition requests.
 */
object AppCheckHelper {

    private const val TAG = "AppCheckHelper"

    @Volatile
    private var isProviderInstalled = false

    internal fun isConfigured(
        projectId: String = BuildConfig.FIREBASE_PROJECT_ID,
        appId: String = BuildConfig.FIREBASE_APP_ID,
        apiKey: String = BuildConfig.FIREBASE_API_KEY
    ): Boolean = projectId.isNotBlank() && appId.isNotBlank() && apiKey.isNotBlank()

    @Synchronized
    fun initIfConfigured(context: Context): Boolean {
        if (!isConfigured()) return false
        return try {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build()
                FirebaseApp.initializeApp(appContext, options)
            }
            if (!isProviderInstalled) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                isProviderInstalled = true
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "App Check init failed (soft fallback): ${t.message}")
            false
        }
    }

    fun getAppCheckTokenOrNull(context: Context, timeoutMs: Long = 2500L): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Skipping blocking App Check fetch on main thread")
            return null
        }
        if (!initIfConfigured(context)) return null
        return try {
            val tokenResult = Tasks.await(
                FirebaseAppCheck.getInstance().getAppCheckToken(false),
                timeoutMs,
                TimeUnit.MILLISECONDS
            )
            tokenResult.token.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "App Check token unavailable (soft mode fallback): ${t.message}")
            null
        }
    }

    internal fun buildShazamTokenRequest(
        endpointUrl: String,
        appVersion: String = BuildConfig.VERSION_NAME,
        appCheckToken: String?
    ): Request = Request.Builder()
        .url(endpointUrl)
        .header("x-web-client", "songflip-app")
        .header("x-app-version", appVersion)
        .header("x-client-platform", "android")
        .apply {
            if (!appCheckToken.isNullOrBlank()) {
                header("X-Firebase-AppCheck", appCheckToken)
            }
        }
        .get()
        .build()
}
