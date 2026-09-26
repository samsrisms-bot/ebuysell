package com.simivr.app.flow

import android.content.Context
import com.simivr.app.data.dao.FlowDao
import com.simivr.app.data.entity.FlowEntity
import com.simivr.app.flow.model.FlowJson
import com.simivr.app.flow.model.IvrFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlowRepository @Inject constructor(
    private val context: Context,
    private val flowDao: FlowDao
) {
    private val sampleAssets = listOf(
        "flows/sample_incoming_flow.json" to true,
        "flows/sample_outbound_flow.json" to true
    )

    /** Copies bundled sample flows into Room the first time the app runs. */
    suspend fun seedSampleFlowsIfNeeded() {
        if (flowDao.count() > 0) return
        for ((asset, isSample) in sampleAssets) {
            val json = context.assets.open(asset).bufferedReader().use { it.readText() }
            val flow = FlowJson.fromJson(json)
            flowDao.upsert(FlowEntity(id = flow.id, name = flow.name, description = flow.description, json = json, isSample = isSample))
        }
    }

    fun observeFlows(): Flow<List<FlowEntity>> = flowDao.observeAll()

    fun observeFlowSummaries(): Flow<List<IvrFlow>> = flowDao.observeAll().map { list -> list.map { FlowJson.fromJson(it.json) } }

    suspend fun getFlow(id: String): IvrFlow? = flowDao.getById(id)?.let { FlowJson.fromJson(it.json) }

    suspend fun saveFlow(flow: IvrFlow, isSample: Boolean = false) {
        flowDao.upsert(
            FlowEntity(
                id = flow.id,
                name = flow.name,
                description = flow.description,
                json = FlowJson.toJson(flow),
                isSample = isSample
            )
        )
    }

    suspend fun deleteFlow(entity: FlowEntity) = flowDao.delete(entity)

    /** Export a single flow as pretty JSON for backup/sharing. */
    suspend fun exportFlowJson(id: String): String? = flowDao.getById(id)?.json

    /** Import a flow from raw JSON (as produced by [exportFlowJson]), assigning it a new id if requested. */
    suspend fun importFlowJson(json: String): IvrFlow {
        val flow = FlowJson.fromJson(json)
        saveFlow(flow)
        return flow
    }
}
