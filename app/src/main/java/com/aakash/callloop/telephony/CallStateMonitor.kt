package com.aakash.callloop.telephony

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

enum class PhoneCallState {
    IDLE,
    RINGING,
    OFFHOOK
}

class CallStateMonitor(
    private val context: Context,
    private val onStateChanged: (PhoneCallState) -> Unit
) {
    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    fun register() {
        if (telephonyManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerModernCallback()
        } else {
            registerLegacyListener()
        }
    }

    fun unregister() {
        if (telephonyManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let { callback ->
                try {
                    telephonyManager.unregisterTelephonyCallback(callback)
                } catch (_: Exception) {}
            }
            modernCallback = null
        } else {
            legacyListener?.let { listener ->
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {}
            }
            legacyListener = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleState(state)
            }
        }
        modernCallback = callback
        val executor: Executor = context.mainExecutor
        try {
            telephonyManager?.registerTelephonyCallback(executor, callback)
        } catch (e: Exception) {
            // Permission missing or security exception
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener() {
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleState(state)
            }
        }
        legacyListener = listener
        try {
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            // Permission missing or security exception
        }
    }

    private fun handleState(state: Int) {
        val mappedState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> PhoneCallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> PhoneCallState.OFFHOOK
            else -> PhoneCallState.IDLE
        }
        onStateChanged(mappedState)
    }
}
