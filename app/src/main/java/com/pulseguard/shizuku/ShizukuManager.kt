package com.pulseguard.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Coarse Shizuku availability, drives the setup wizard and graceful degradation. */
enum class ShizukuStatus {
    /** Shizuku app is not installed at all. */
    NOT_INSTALLED,

    /** Installed, but the Shizuku service (binder) is not running — user must start it. */
    NOT_RUNNING,

    /** Running, but PulseGuard has not been granted the Shizuku permission yet. */
    PERMISSION_REQUIRED,

    /** Running and permitted — privileged shell is available. */
    READY,
    ;

    val isReady: Boolean get() = this == READY
}

/**
 * Owns everything Shizuku: liveness tracking, permission, and the [IUserService] shell
 * bridge. A single instance lives on the [PulseGuardApp]. Safe to call from any thread;
 * [exec] hops to IO.
 */
class ShizukuManager(private val appContext: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    private var boundService: IUserService? = null
    private val bindMutex = Mutex()

    // Written on the binding coroutine, completed from the main-thread ServiceConnection callback.
    @Volatile
    private var connectDeferred: CompletableDeferred<IUserService?>? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(SERVICE_VERSION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service =
                if (binder != null && binder.pingBinder()) IUserService.Stub.asInterface(binder)
                else null
            boundService = service
            connectDeferred?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        boundService = null
        refresh()
    }
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    /** Call once from Application.onCreate. */
    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    /** Recompute [status] from the current binder + permission state. Cheap; call freely. */
    fun refresh() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        if (!isShizukuInstalled()) return ShizukuStatus.NOT_INSTALLED
        if (!pingBinderSafely()) return ShizukuStatus.NOT_RUNNING
        return try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.PERMISSION_REQUIRED
            }
        } catch (t: Throwable) {
            // Binder went away between ping and check.
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun isShizukuInstalled(): Boolean =
        SHIZUKU_PACKAGES.any { pkg ->
            try {
                appContext.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    /** True only when the binder is live and permission is granted. */
    fun isReady(): Boolean = _status.value.isReady

    fun shouldShowRationale(): Boolean =
        try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (t: Throwable) {
            false
        }

    /**
     * Kicks off the Shizuku permission prompt. The outcome arrives asynchronously via the
     * permission listener, which refreshes [status]. No-op if the binder isn't alive.
     */
    fun requestPermission() {
        if (!pingBinderSafely()) return
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh()
                return
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Runs a single shell command with shell privileges. Returns [ShellResult.UNAVAILABLE]
     * (never throws) if Shizuku isn't ready or the service can't be bound.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val service = ensureService() ?: return@withContext ShellResult.UNAVAILABLE
        try {
            ShellResult.decode(service.exec(command))
        } catch (e: RemoteException) {
            boundService = null
            ShellResult(exitCode = -98, output = "RemoteException: ${e.message}")
        } catch (t: Throwable) {
            ShellResult(exitCode = -97, output = "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private suspend fun ensureService(): IUserService? {
        aliveOrNull(boundService)?.let { return it }
        if (!isReady()) return null
        return bindMutex.withLock {
            aliveOrNull(boundService)?.let { return@withLock it }
            if (!isReady()) return@withLock null
            bindInternal()
        }
    }

    private suspend fun bindInternal(): IUserService? {
        val deferred = CompletableDeferred<IUserService?>()
        connectDeferred = deferred
        return try {
            Shizuku.bindUserService(userServiceArgs, connection)
            withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }?.let { aliveOrNull(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "bindUserService failed", t)
            null
        }
    }

    private fun aliveOrNull(service: IUserService?): IUserService? {
        val binder = service?.asBinder() ?: return null
        return if (binder.isBinderAlive && binder.pingBinder()) service else null
    }

    private fun pingBinderSafely(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }

    private companion object {
        const val TAG = "ShizukuManager"
        const val SERVICE_VERSION = 1
        const val PERMISSION_REQUEST_CODE = 4210
        const val BIND_TIMEOUT_MS = 8_000L
        val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api")
    }
}
