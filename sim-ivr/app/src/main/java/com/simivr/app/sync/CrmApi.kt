package com.simivr.app.sync

import com.simivr.app.sync.model.CallCrmEvent
import com.simivr.app.sync.model.CrmContactDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CrmApi {
    @POST("api/ivr/events")
    suspend fun postEvent(@Body event: CallCrmEvent): Response<Unit>

    @GET("api/ivr/campaigns/{id}/contacts")
    suspend fun getCampaignContacts(@Path("id") campaignId: String): List<CrmContactDto>
}
