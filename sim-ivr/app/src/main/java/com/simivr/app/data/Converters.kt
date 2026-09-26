package com.simivr.app.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.simivr.app.data.entity.CallDirection
import com.simivr.app.data.entity.CallOutcome
import com.simivr.app.data.entity.CallerRuleType
import com.simivr.app.data.entity.CampaignStatus
import com.simivr.app.data.entity.ContactStatus
import com.simivr.app.data.entity.OutboxStatus

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun listToJson(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun jsonToList(value: String): List<String> =
        gson.fromJson(value, Array<String>::class.java)?.toList() ?: emptyList()

    @TypeConverter
    fun fromCallerRuleType(value: CallerRuleType) = value.name

    @TypeConverter
    fun toCallerRuleType(value: String) = CallerRuleType.valueOf(value)

    @TypeConverter
    fun fromCampaignStatus(value: CampaignStatus) = value.name

    @TypeConverter
    fun toCampaignStatus(value: String) = CampaignStatus.valueOf(value)

    @TypeConverter
    fun fromContactStatus(value: ContactStatus) = value.name

    @TypeConverter
    fun toContactStatus(value: String) = ContactStatus.valueOf(value)

    @TypeConverter
    fun fromCallDirection(value: CallDirection) = value.name

    @TypeConverter
    fun toCallDirection(value: String) = CallDirection.valueOf(value)

    @TypeConverter
    fun fromCallOutcome(value: CallOutcome) = value.name

    @TypeConverter
    fun toCallOutcome(value: String) = CallOutcome.valueOf(value)

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus) = value.name

    @TypeConverter
    fun toOutboxStatus(value: String) = OutboxStatus.valueOf(value)
}
