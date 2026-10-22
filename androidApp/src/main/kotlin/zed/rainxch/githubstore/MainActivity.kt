package zed.rainxch.githubstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.app.deeplink.DeepLinkParser
import zed.rainxch.githubstore.utils.updateSystemBars

class MainActivity : ComponentActivity() {
    private var deepLinkUri by mutableStateOf<String?>(null)

    private val shareManager: ShareManager by inject()
    private val localizationManager: LocalizationManager by inject()
    private val tweaksRepository: TweaksRepository by inject()
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase by inject()
    private val appScope: CoroutineScope by inject()

    private val contentPainted =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

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
