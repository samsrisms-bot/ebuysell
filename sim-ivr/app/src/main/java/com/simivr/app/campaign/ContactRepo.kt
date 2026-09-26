package com.simivr.app.campaign

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.entity.ContactEntity
import com.simivr.app.sync.CrmApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports campaign contact lists from CSV (name, phone, arbitrary custom columns become
 * {varName} values usable in TTS personalization) and from the CRM's contacts endpoint.
 */
@Singleton
class ContactRepo @Inject constructor(
    private val context: Context,
    private val contactDao: ContactDao,
    private val crmApi: CrmApi
) {
    private val gson = Gson()

    /** Pulls contacts from GET {base}/api/ivr/campaigns/{remoteCampaignId}/contacts into [campaignId]. */
    suspend fun pullFromCrm(campaignId: String, remoteCampaignId: String): Int {
        val rows = crmApi.getCampaignContacts(remoteCampaignId)
        val contacts = rows.map {
            ContactEntity(
                campaignId = campaignId,
                name = it.name ?: "",
                phone = it.phone,
                varsJson = gson.toJson(it.vars ?: emptyMap<String, String>())
            )
        }
        contactDao.insertAll(contacts)
        return contacts.size
    }

    /** Parses a CSV file (header row required, must include "name" and "phone" columns) into contacts for [campaignId]. */
    suspend fun importCsv(uri: Uri, campaignId: String): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return 0
        return importCsvText(text, campaignId)
    }

    suspend fun importCsvText(text: String, campaignId: String): Int {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0
        val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val nameIdx = header.indexOf("name")
        val phoneIdx = header.indexOf("phone")
        if (phoneIdx == -1) return 0

        val contacts = lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            if (cols.size <= phoneIdx || cols[phoneIdx].isBlank()) return@mapNotNull null
            val vars = mutableMapOf<String, String>()
            header.forEachIndexed { i, key ->
                if (i != nameIdx && i != phoneIdx && i < cols.size) vars[key] = cols[i]
            }
            ContactEntity(
                campaignId = campaignId,
                name = if (nameIdx != -1 && nameIdx < cols.size) cols[nameIdx] else "",
                phone = cols[phoneIdx].trim(),
                varsJson = gson.toJson(vars)
            )
        }
        contactDao.insertAll(contacts)
        return contacts.size
    }

    /** RFC4180-ish CSV line parser: handles quoted fields containing commas. */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
