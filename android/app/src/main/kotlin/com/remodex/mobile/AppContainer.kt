package com.remodex.mobile

import android.content.Context
import com.remodex.mobile.core.persistence.AIChangeSetPersistence
import com.remodex.mobile.core.persistence.CodexMessagePersistence
import com.remodex.mobile.core.persistence.SessionPersistence
import com.remodex.mobile.core.security.SecureStore
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
    }
}
