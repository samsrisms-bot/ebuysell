package com.simivr.app.telecom

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio routing (speakerphone on/off) is a method on [android.telecom.InCallService] itself, not
 * on [android.telecom.Call] — this small holder lets other classes (CallController,
 * CallFlowActionHandler) reach the live service instance without the system needing to know about
 * Hilt injection points on a component it constructs itself.
 */
@Singleton
class InCallServiceHolder @Inject constructor() {
    @Volatile
    var service: IvrInCallService? = null
}
