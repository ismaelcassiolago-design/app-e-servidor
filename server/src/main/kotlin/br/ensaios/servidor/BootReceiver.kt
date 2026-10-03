package br.ensaios.servidor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Liga o servidor sozinho quando o celular reinicia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.autoStart(context)) {
            ServerService.start(context)
        }
    }
}
