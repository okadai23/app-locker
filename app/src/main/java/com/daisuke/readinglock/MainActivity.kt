package com.daisuke.readinglock

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.daisuke.readinglock.data.Settings
import kotlinx.coroutines.launch
import com.daisuke.readinglock.system.DnsVpnService
import com.daisuke.readinglock.system.MonitorService
import com.daisuke.readinglock.ui.ReadingLockNav
import com.daisuke.readinglock.ui.Routes
import com.daisuke.readinglock.ui.setup.setupComplete
import com.daisuke.readinglock.ui.theme.ReadingLockTheme

/** 単一 Activity。オーバーレイ・通知からのディープリンクはここで route に変換する */
class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<String?>(null)

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) enableSiteBlock()
    }

    /** ウィザードからの明示的な有効化。OFF フラグが残っていても ON に戻す */
    private fun enableSiteBlock() {
        lifecycleScope.launch { AppGraph.settings.setSiteBlockEnabled(true) }
        DnsVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorService.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        deepLink = routeOf(intent)

        setContent {
            val settings by AppGraph.settings.flow.collectAsState(Settings())
            // 権限は保存フラグではなく実状態で判定する。§8
            // startDestination は最初の composition でしか効かないので、判定が出るまで描かない
            val complete by produceState<Boolean?>(null) { value = setupComplete(this@MainActivity) }
            ReadingLockTheme(settings.theme) {
                if (complete == null) return@ReadingLockTheme
                ReadingLockNav(
                    startAtSetup = complete == false,
                    deepLink = deepLink,
                    onDeepLinkHandled = { deepLink = null },
                    onRequestVpn = {
                        val consent = DnsVpnService.prepareIntent(this)
                        if (consent != null) vpnConsent.launch(consent) else enableSiteBlock()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = routeOf(intent)
    }

    private fun routeOf(intent: Intent?): String? = when (intent?.data?.host) {
        AppIntents.HOST_START -> Routes.FLOW_SOURCE
        AppIntents.HOST_HOME -> Routes.HOME
        else -> null
    }
}
