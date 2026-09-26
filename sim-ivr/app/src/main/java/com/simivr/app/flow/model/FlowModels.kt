package com.simivr.app.flow.model

/** A complete IVR flow: a graph of [FlowNode]s reachable from [entryNodeId]. Stored as JSON in Room. */
data class IvrFlow(
    val id: String,
    val name: String,
    val description: String = "",
    val entryNodeId: String,
    val nodes: List<FlowNode>,
    val version: Int = 1
) {
    fun nodeById(id: String): FlowNode? = nodes.firstOrNull { it.id == id }
}

/** Where the audio for a Play/Menu/Voicemail prompt comes from. */
sealed class AudioSource {
    data class Tts(val text: String, val locale: String = "en-IN") : AudioSource()
    data class File(val path: String) : AudioSource()
}

enum class TransferMode { CONFERENCE_MERGE, CALL_FORWARD }

/** One node in the flow graph. [id] must be unique within a flow. */
sealed class FlowNode {
    abstract val id: String

    data class Play(
        override val id: String,
        val audio: AudioSource,
        val next: String?
    ) : FlowNode()

    data class Menu(
        override val id: String,
        val prompt: AudioSource,
        /** digit pressed -> id of next node */
        val options: Map<String, String>,
        val timeoutMs: Long = 6000L,
        val timeoutNext: String? = null,
        val invalidPrompt: AudioSource? = null,
        val maxInvalidRetries: Int = 2,
        val invalidNext: String? = null
    ) : FlowNode()

    data class CollectDigits(
        override val id: String,
        val prompt: AudioSource,
        val numDigits: Int,
        val terminatorDigit: Char? = '#',
        val timeoutMs: Long = 10000L,
        /** name this collected value is stored under, for use in later TTS templates or CRM vars */
        val variableName: String,
        val next: String?
    ) : FlowNode()

    data class Transfer(
        override val id: String,
        val destinationNumber: String,
        val mode: TransferMode = TransferMode.CONFERENCE_MERGE,
        val next: String? = null
    ) : FlowNode()

    data class Voicemail(
        override val id: String,
        val prompt: AudioSource,
        val playConsentLine: Boolean = true,
        val maxDurationMs: Long = 60000L,
        val next: String?
    ) : FlowNode()

    data class Webhook(
        override val id: String,
        val url: String,
        val method: String = "POST",
        val next: String?
    ) : FlowNode()

    data class Hangup(override val id: String) : FlowNode()
}
