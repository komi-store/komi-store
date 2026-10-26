package zed.rainxch.core.data.services.dhizuku

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import co.touchlab.kermit.Logger
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.services.dhizuku.model.DhizukuStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class DhizukuServiceManager(
    private val context: Context,
) {
    private val _status = MutableStateFlow(DhizukuStatus.NOT_INSTALLED)
    val status: StateFlow<DhizukuStatus> = _status.asStateFlow()

    private val bindMutex = Mutex()
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    var installerService: IDhizukuInstallerService? = null
        private set

    fun initialize() {
        runCatching { Dhizuku.init(context) }
            .onFailure { Logger.w(TAG) { "warm-up Dhizuku.init() threw: ${it.message}" } }
        refreshStatus()
    }

    fun refreshStatus() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): DhizukuStatus {
        if (!isPackageInstalled(DHIZUKU_PACKAGE)) {
            Logger.d(TAG) { "computeStatus() — Dhizuku package not installed" }
            return DhizukuStatus.NOT_INSTALLED
        }
        val initialized = initDhizuku()
        Logger.d(TAG) { "computeStatus() — Dhizuku.init=$initialized" }
        return when {
            !initialized -> {
                DhizukuStatus.NOT_RUNNING
            }

            isPermissionGranted() -> {
                Logger.d(TAG) { "computeStatus() — READY" }
                DhizukuStatus.READY
            }

            else -> {
                DhizukuStatus.PERMISSION_NEEDED
            }
        }
    }

    private fun initDhizuku(): Boolean {
        // Dhizuku.init dereferences DevicePolicyManager, which devices without the device_admin feature lack.
        if (context.getSystemService(DevicePolicyManager::class.java) == null) return false
        return try {
            Dhizuku.init(context)
        } catch (e: IllegalStateException) {
            Logger.w(TAG) { "Dhizuku.init() threw: ${e.message}" }
            false
        } catch (e: SecurityException) {
            Logger.w(TAG) { "Dhizuku.init() threw: ${e.message}" }
            false
        }
    }

    private fun isPermissionGranted(): Boolean = permissionGrantedOrNull() ?: false

    // Dhizuku rethrows a dead binder's RemoteException as a bare RuntimeException, so no narrower catch exists.
    private fun permissionGrantedOrNull(): Boolean? =
        try {
            Dhizuku.isPermissionGranted()
        } catch (e: Exception) {
            Logger.w(TAG) { "Dhizuku.isPermissionGranted() threw: ${e.message}" }
            null
        }

    private fun isPackageInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    fun requestPermission() {
        if (!initDhizuku()) {
            Logger.w(TAG) { "requestPermission() — Dhizuku.init() failed, cannot request" }
            refreshStatus()
            return
        }
        when (permissionGrantedOrNull()) {
            true -> refreshStatus()
            false -> launchPermissionRequest()
            null -> Unit
        }
    }

    private fun launchPermissionRequest() {
        try {
            Dhizuku.requestPermission(
                object : DhizukuRequestPermissionListener() {
                    @Throws(RemoteException::class)
                    override fun onRequestPermission(grantResult: Int) {
                        Logger.d(TAG) {
                            "Dhizuku permission result: granted=${grantResult == PackageManager.PERMISSION_GRANTED}"
                        }
                        refreshStatus()
                    }
                },
            )
        } catch (e: IllegalStateException) {
            Logger.w(TAG) { "Failed to request Dhizuku permission: ${e.message}" }
        } catch (e: SecurityException) {
            Logger.w(TAG) { "Failed to request Dhizuku permission: ${e.message}" }
        }
    }

    suspend fun getService(): IDhizukuInstallerService? {
        Logger.d(TAG) { "getService() — current status=${_status.value}" }
        if (_status.value != DhizukuStatus.READY) {
            Logger.w(TAG) { "getService() — Dhizuku not READY (status=${_status.value}), returning null" }
            return null
        }

        return bindMutex.withLock {
            val cached = installerService
            if (cached == null) {
                Logger.d(TAG) { "getService() — no cached service, binding..." }
            } else {
                val alive = cached.asBinder().pingBinder()
                Logger.d(TAG) { "getService() — cached service ping=$alive" }
                if (alive) return@withLock cached
                Logger.w(TAG) { "getService() — cached service binder dead, rebinding..." }
                installerService = null
            }

            bindService()
        }
    }

    private suspend fun bindService(): IDhizukuInstallerService? {
        Logger.d(TAG) { "bindService() — attempting to bind Dhizuku UserService..." }
        return try {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val componentName =
                        ComponentName(
                            context.packageName,
                            DhizukuInstallerServiceImpl::class.java.name,
                        )
                    Logger.d(TAG) { "bindService() — component: $componentName" }

                    val args = DhizukuUserServiceArgs(componentName)

                    val connection =
                        object : ServiceConnection {
                            override fun onServiceConnected(
                                name: ComponentName?,
                                binder: IBinder?,
                            ) {
                                Logger.d(TAG) {
                                    "onServiceConnected() — name=$name, binderAlive=${binder?.pingBinder()}"
                                }
                                val service = IDhizukuInstallerService.Stub.asInterface(binder)
                                installerService = service
                                Logger.d(TAG) { "Dhizuku installer service connected and cached" }
                                if (continuation.isActive) {
                                    continuation.resume(service)
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                installerService = null
                                Logger.d(TAG) { "Dhizuku installer service disconnected: $name" }
                            }
                        }

                    serviceConnection = connection

                    Logger.d(TAG) { "Calling Dhizuku.bindUserService()..." }
                    val bound = Dhizuku.bindUserService(args, connection)
                    Logger.d(TAG) { "Dhizuku.bindUserService() returned: $bound" }
                    if (!bound && continuation.isActive) {
                        continuation.resume(null)
                    }

                    continuation.invokeOnCancellation {
                        Logger.d(TAG) { "bindService() coroutine cancelled, unbinding..." }
                        try {
                            Dhizuku.unbindUserService(connection)
                        } catch (_: Exception) {
                        }
                    }
                }
            }.also { service ->
                if (service == null) {
                    Logger.w(TAG) { "bindService() timed out or failed after ${BIND_TIMEOUT_MS}ms" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            bindFailed(e)
        } catch (e: SecurityException) {
            bindFailed(e)
        }
    }

    private fun bindFailed(e: RuntimeException): IDhizukuInstallerService? {
        Logger.e(TAG) { "Failed to bind Dhizuku service: ${e.javaClass.simpleName}: ${e.message}" }
        Logger.e(TAG) { e.stackTraceToString() }
        return null
    }

    companion object {
        private const val TAG = "DhizukuServiceManager"
        private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
        private const val BIND_TIMEOUT_MS = 15_000L
    }
}
