package com.simivr.app.telecom

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresPermission
import javax.inject.Inject
import javax.inject.Singleton

data class SimChoice(val slotIndex: Int, val displayName: String, val handle: PhoneAccountHandle)

/** Resolves the app's own SIM 1 / SIM 2 slot index (0/1) to the [PhoneAccountHandle] TelecomManager needs to place a call on that SIM. */
@Singleton
class SimAccountResolver @Inject constructor(private val context: Context) {

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun listSimChoices(): List<SimChoice> {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handles = try {
            telecomManager.callCapablePhoneAccountHandles
        } catch (_: SecurityException) {
            emptyList()
        }
        return handles.mapIndexedNotNull { index, handle ->
            val account = try {
                telecomManager.getPhoneAccount(handle)
            } catch (_: SecurityException) {
                null
            }
            SimChoice(
                slotIndex = index,
                displayName = account?.label?.toString() ?: "SIM ${index + 1}",
                handle = handle
            )
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun handleForSlot(slotIndex: Int): PhoneAccountHandle? = listSimChoices().getOrNull(slotIndex)?.handle
}
