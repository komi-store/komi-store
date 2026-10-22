package zed.rainxch.githubstore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.data.utils.AndroidShareManager
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.app.deeplink.DeepLinkParser
import zed.rainxch.githubstore.utils.applyDisplayModePreference
import zed.rainxch.githubstore.utils.updateSystemBars

class MainActivity : ComponentActivity() {
    private var deepLinkUri by mutableStateOf<String?>(null)

    private val shareManager: ShareManager by inject()
    private val localizationManager: LocalizationManager by inject()
    private val tweaksRepository: TweaksRepository by inject()
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase by inject()
    private val appScope: CoroutineScope by inject()
    private val systemInstallSerializer: SystemInstallSerializer by inject()

    private val contentPainted =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    private var localNetworkPromptedFor = emptySet<String>()
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()

        (shareManager as? AndroidShareManager)?.registerActivityResultLauncher(this)

        super.onCreate(savedInstanceState)

        // Cancels draw requests until the real UI past the gate has composed, so no placeholder
        // frame is rendered.
        getViewModel<MainViewModel>()
        splash.setKeepOnScreenCondition { !contentPainted.get() }

        handleIncomingIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tweaksRepository
                    .getAppLanguage()
                    .drop(1)
                    .collect { newTag ->
                        localizationManager.setActiveLanguageTag(newTag)
                        recreate()
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tweaksRepository
                    .getDisplayModeId()
                    .collect { modeId -> this@MainActivity.applyDisplayModePreference(modeId) }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            requestLocalNetworkAccessWhenNeeded()
        }

        setContent {
            DisposableEffect(Unit) {
                val listener =
                    Consumer<Intent> { newIntent ->
                        handleIncomingIntent(newIntent)
                    }

                addOnNewIntentListener(listener)

                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            App(
                deepLinkUri = deepLinkUri,
                onResolvedDarkTheme = { isDarkTheme ->
                    this@MainActivity.updateSystemBars(isDarkTheme)
                },
                onContentPainted = { contentPainted.set(true) },
            )
        }
    }

    // Android 17 blocks LAN traffic without ACCESS_LOCAL_NETWORK. While the activity lives, asks again only when the
    // set of LAN hosts in the proxy, forge or LibreTranslate settings changes; after two denials Android stops showing
    // the prompt.
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun requestLocalNetworkAccessWhenNeeded() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                localNetworkHosts(tweaksRepository).collect { hosts ->
                    val granted =
                        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
                    if (hosts.isNotEmpty() && hosts != localNetworkPromptedFor && !granted) {
                        localNetworkPromptedFor = hosts
                        localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        systemInstallSerializer.onAppResumed()
    }

    override fun onRestart() {
        super.onRestart()
        appScope.launch {
            runCatching { syncInstalledAppsUseCase() }
                .onFailure {
                    Logger.w(it) { "onRestart sync failed" }
                }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val uriString =
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    intent.data?.toString()
                }

                Intent.ACTION_SEND -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let { DeepLinkParser.extractSupportedUrl(it) }
                }

                else -> {
                    null
                }
            }

        uriString?.let { deepLinkUri = it }
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
