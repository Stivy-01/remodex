package com.remodex.mobile

import android.app.Application
import com.remodex.mobile.core.notification.AppForegroundTracker
import com.remodex.mobile.core.notification.RemodexLocalNotificationPresenter
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class RemodexApplication : Application() {
    override fun onCreate() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        super.onCreate()
        AppForegroundTracker.register()
        AppContainer.initialize(this)
        RemodexLocalNotificationPresenter.ensureChannelCreated(this)
    }
}
