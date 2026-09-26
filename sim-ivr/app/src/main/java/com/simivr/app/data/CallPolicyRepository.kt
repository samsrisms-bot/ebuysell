package com.simivr.app.data

import com.simivr.app.data.dao.CallerRuleDao
import com.simivr.app.data.dao.DndDao
import com.simivr.app.data.entity.CallerRuleType
import com.simivr.app.data.entity.DndEntity
import javax.inject.Inject
import javax.inject.Singleton

enum class ScreeningDecision { ALLOW, BLOCK }

/**
 * Decides whether an incoming number should be answered, and whether an outbound campaign
 * number must be skipped for TRAI/TCCCP DND compliance.
 */
@Singleton
class CallPolicyRepository @Inject constructor(
    private val callerRuleDao: CallerRuleDao,
    private val dndDao: DndDao
) {
    suspend fun screenIncoming(number: String): ScreeningDecision {
        val normalized = normalize(number)
        if (callerRuleDao.exists(normalized, CallerRuleType.BLOCKLIST)) return ScreeningDecision.BLOCK
        val allowlist = callerRuleDao.getByType(CallerRuleType.ALLOWLIST)
        if (allowlist.isNotEmpty() && allowlist.none { normalize(it.phoneNumber) == normalized }) {
            return ScreeningDecision.BLOCK
        }
        return ScreeningDecision.ALLOW
    }

    suspend fun isOnDndList(number: String): Boolean = dndDao.isOnDndList(normalize(number))

    suspend fun addToDndList(number: String, reason: String = "opt-out") =
        dndDao.insert(DndEntity(phoneNumber = normalize(number), reason = reason))

    suspend fun removeFromDndList(number: String) = dndDao.remove(normalize(number))

    private fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' }
}
