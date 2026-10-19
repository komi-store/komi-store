package zed.rainxch.core.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.domain.model.mirror.MirrorPreference
import zed.rainxch.core.domain.model.mirror.TrafficKind
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.repository.MirrorRepository
import zed.rainxch.core.domain.repository.ProxyRepository
import java.util.concurrent.atomic.AtomicReference

object ProxyManager {
    private val flows: Map<ProxyScope, MutableStateFlow<ProxyConfig>> =
        ProxyScope.entries.associateWith { MutableStateFlow<ProxyConfig>(ProxyConfig.System) }

    private val mirror = AtomicReference<MirrorActive?>(null)
    private var mirrorCollectorJob: Job? = null

    private val seedMutex = Mutex()

    @Volatile private var seedJob: Job? = null

    fun configFlow(scope: ProxyScope): StateFlow<ProxyConfig> = flows.getValue(scope).asStateFlow()

    fun bootstrap(
        repository: ProxyRepository,
        appScope: CoroutineScope,
    ) {
        if (seedJob != null) return
        appScope.launch {
            seedMutex.withLock {
                if (seedJob != null) return@withLock
                seedJob =
                    launch {
                        ProxyScope.entries.forEach { scope ->
                            runCatching {
                                val cfg = repository.getProxyConfig(scope).first()
                                flows.getValue(scope).value = cfg
                            }
                        }
                    }
            }
        }
    }

    fun currentConfig(scope: ProxyScope): ProxyConfig = flows.getValue(scope).value

    fun setConfig(
        scope: ProxyScope,
        config: ProxyConfig,
    ) {
        flows.getValue(scope).value = config
    }

    fun currentMirror(): MirrorActive? = mirror.get()

    fun currentMirrorTemplate(): String? = mirror.get()?.template

    fun startMirrorCollector(
        repository: MirrorRepository,
        scope: CoroutineScope,
    ) {
        if (mirrorCollectorJob?.isActive == true) return
        mirrorCollectorJob =
            scope.launch {
                combine(
                    repository.observePreference(),
                    repository.observeCatalog(),
                ) { pref, catalog ->
                    when (pref) {
                        MirrorPreference.Direct -> {
                            null
                        }

                        is MirrorPreference.Custom -> {
                            MirrorActive(
                                template = pref.template,
                                trafficKinds = setOf(TrafficKind.RELEASE_ASSET, TrafficKind.RAW_FILE),
                            )
                        }

                        is MirrorPreference.Selected -> {
                            val cfg = catalog.firstOrNull { it.id == pref.id }
                            val template = cfg?.urlTemplate
                            if (cfg == null || template == null) {
                                null
                            } else {
                                MirrorActive(template = template, trafficKinds = cfg.trafficKinds)
                            }
                        }
                    }
                }.collect { active ->
                    mirror.set(active)
                }
            }
    }
}
