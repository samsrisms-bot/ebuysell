package com.simivr.app.ui.flows

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.flow.FlowRepository
import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.FlowNode
import com.simivr.app.flow.model.IvrFlow
import com.simivr.app.ui.common.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class NodeKind { PLAY, MENU, COLLECT, TRANSFER, VOICEMAIL, WEBHOOK, HANGUP }

fun FlowNode.kind(): NodeKind = when (this) {
    is FlowNode.Play -> NodeKind.PLAY
    is FlowNode.Menu -> NodeKind.MENU
    is FlowNode.CollectDigits -> NodeKind.COLLECT
    is FlowNode.Transfer -> NodeKind.TRANSFER
    is FlowNode.Voicemail -> NodeKind.VOICEMAIL
    is FlowNode.Webhook -> NodeKind.WEBHOOK
    is FlowNode.Hangup -> NodeKind.HANGUP
}

fun defaultNodeFor(kind: NodeKind, id: String): FlowNode = when (kind) {
    NodeKind.PLAY -> FlowNode.Play(id, AudioSource.Tts("New prompt"), null)
    NodeKind.MENU -> FlowNode.Menu(id, AudioSource.Tts("Press 1 for..."), mapOf("1" to id))
    NodeKind.COLLECT -> FlowNode.CollectDigits(id, AudioSource.Tts("Please enter your code"), 4, '#', 10000, "code", null)
    NodeKind.TRANSFER -> FlowNode.Transfer(id, "")
    NodeKind.VOICEMAIL -> FlowNode.Voicemail(id, AudioSource.Tts("Please leave a message"), next = null)
    NodeKind.WEBHOOK -> FlowNode.Webhook(id, "https://", next = null)
    NodeKind.HANGUP -> FlowNode.Hangup(id)
}

@HiltViewModel
class FlowEditorViewModel @Inject constructor(
    private val flowRepository: FlowRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val flowIdArg: String = savedStateHandle.get<String>("flowId") ?: NavRoutes.NEW_FLOW_ID
    val isNew = flowIdArg == NavRoutes.NEW_FLOW_ID

    private val _flow = MutableStateFlow(emptyFlow())
    val flow: StateFlow<IvrFlow> = _flow.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                flowRepository.getFlow(flowIdArg)?.let { _flow.value = it }
            }
        }
    }

    private fun emptyFlow(): IvrFlow {
        val greetingId = "greeting"
        val hangupId = "hangup"
        return IvrFlow(
            id = UUID.randomUUID().toString(),
            name = "New flow",
            description = "",
            entryNodeId = greetingId,
            nodes = listOf(
                FlowNode.Play(greetingId, AudioSource.Tts("Hello, welcome."), hangupId),
                FlowNode.Hangup(hangupId)
            )
        )
    }

    fun updateMeta(name: String, description: String) {
        _flow.value = _flow.value.copy(name = name, description = description)
    }

    fun setEntryNode(nodeId: String) {
        _flow.value = _flow.value.copy(entryNodeId = nodeId)
    }

    fun addNode(kind: NodeKind) {
        val current = _flow.value
        var idx = current.nodes.size + 1
        var id = "node_$idx"
        while (current.nodes.any { it.id == id }) { idx++; id = "node_$idx" }
        _flow.value = current.copy(nodes = current.nodes + defaultNodeFor(kind, id))
    }

    fun replaceNode(oldId: String, newNode: FlowNode) {
        _flow.value = _flow.value.copy(nodes = _flow.value.nodes.map { if (it.id == oldId) newNode else it })
    }

    fun deleteNode(nodeId: String) {
        val current = _flow.value
        if (current.nodes.size <= 1) return
        val remaining = current.nodes.filterNot { it.id == nodeId }
        val newEntry = if (current.entryNodeId == nodeId) remaining.first().id else current.entryNodeId
        _flow.value = current.copy(nodes = remaining, entryNodeId = newEntry)
    }

    fun save() {
        viewModelScope.launch {
            flowRepository.saveFlow(_flow.value)
            _saved.value = true
        }
    }
}
