package br.ensaios.servidor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import br.ensaios.servidor.api.ApiServer
import br.ensaios.servidor.db.ServerDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Serviço em primeiro plano que mantém o servidor HTTP ligado o tempo todo,
 * com uma notificação fixa (exigência do Android para não fechar o app).
 */
class ServerService : Service() {

    companion object {
        const val PORT = 8080
        private const val CHANNEL_ID = "servidor"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "br.ensaios.servidor.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ServerService::class.java).setAction(ACTION_STOP))
        }
    }

    private var server: ApiServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setAutoStart(this, false)
            stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        Prefs.setAutoStart(this, true)
        if (server == null) {
            val api = ApiServer(ServerDb.get(this), PORT)
            server = api
            // Liga fora da thread principal para não travar a tela.
            Thread {
                try {
                    api.start()
                    _running.value = true
                    _lastError.value = null
                } catch (e: Exception) {
                    _lastError.value = e.message ?: e.toString()
                    _running.value = false
                    server = null
                }
            }.start()
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ensaios:servidor").also {
                it.setReferenceCounted(false)
                it.acquire()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun stopServer() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
        _running.value = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Servidor ativo", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("Servidor de ensaios ativo")
            .setContentText("Recebendo sincronizações na porta $PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
