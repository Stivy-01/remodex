package com.remodex.mobile

import android.content.Context
import com.remodex.mobile.beta.BetaDeviceInfo
import com.remodex.mobile.beta.BetaEngagementClient
import com.remodex.mobile.beta.BetaEngagementRepository
import com.remodex.mobile.beta.BetaTesterStore
import com.remodex.mobile.beta.SharedPreferencesBetaKeyValueStore
import com.remodex.mobile.core.persistence.AIChangeSetPersistence
import com.remodex.mobile.core.persistence.CodexMessagePersistence
import com.remodex.mobile.core.persistence.SessionPersistence
import com.remodex.mobile.core.security.SecureStore
import com.remodex.mobile.core.config.FeatureFlags
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.services.CodexService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Application-wide services (secure store, persistence, OkHttp, bridge client). */
object AppContainer {
    private val pendingNotificationThreadLock = Any()

    @Volatile
    private var pendingNotificationThreadId: String? = null

    /** Set when the user taps a local notification ([RemodexLocalNotificationPresenter]). */
    fun setPendingOpenThreadFromNotification(threadId: String?) {
        val t = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        synchronized(pendingNotificationThreadLock) {
            pendingNotificationThreadId = t
        }
    }

    fun consumePendingOpenThreadFromNotification(): String? =
        synchronized(pendingNotificationThreadLock) {
            val v = pendingNotificationThreadId
            pendingNotificationThreadId = null
            v
        }

    lateinit var appContext: Context
        private set

    lateinit var secureStore: SecureStore
        private set

    lateinit var messagePersistence: CodexMessagePersistence
        private set

    lateinit var aiChangeSetPersistence: AIChangeSetPersistence
        private set

    lateinit var sessionPersistence: SessionPersistence
        private set

    lateinit var httpClient: OkHttpClient
        private set

    lateinit var codexRepository: CodexRepository
        private set

    lateinit var betaEngagementRepository: BetaEngagementRepository
        private set

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        secureStore = SecureStore(app)
        messagePersistence = CodexMessagePersistence(app, secureStore)
        aiChangeSetPersistence = AIChangeSetPersistence(app)
        sessionPersistence = SessionPersistence(secureStore, app)
        httpClient =
            OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        codexRepository =
            CodexService(
                context = app,
                httpClient = httpClient,
                secureStore = secureStore,
                sessionPersistence = sessionPersistence,
                messagePersistence = messagePersistence,
            )
        val betaStore = BetaTesterStore(SharedPreferencesBetaKeyValueStore(app))
        val betaApi =
            if (FeatureFlags.betaEngagementEnabled) {
                BetaEngagementClient(
                    httpClient = httpClient,
                    baseUrl = BuildConfig.BETA_API_BASE_URL,
                    apiKey = BuildConfig.BETA_API_KEY,
                )
            } else {
                null
            }
        betaEngagementRepository =
            BetaEngagementRepository(
                enabled = FeatureFlags.betaEngagementEnabled,
                store = betaStore,
                api = betaApi,
                appVersionProvider = { BetaDeviceInfo.appVersionName(app) },
                deviceModelProvider = { BetaDeviceInfo.coarseDeviceModel() },
                deviceKeyProvider = { BetaDeviceInfo.stableBetaDeviceKey(app) },
            )
    }
}
