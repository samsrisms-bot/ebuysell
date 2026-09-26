package com.simivr.app.flow

import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.FlowNode
import com.simivr.app.flow.model.IvrFlow

enum class FlowEndReason { HANGUP_NODE, CALL_ENDED, MAX_STEPS_EXCEEDED, ERROR }

data class FlowRunResult(
    val path: List<String>,
    val digitsPressed: List<String>,
    val vars: Map<String, String>,
    val endReason: FlowEndReason,
    val error: String? = null
)

/**
 * Interprets an [IvrFlow] graph node by node, delegating actual audio/telecom work to a
 * [FlowActionHandler]. Pure state-machine logic lives here so it can be unit-tested without
 * Android or a real call.
 */
class FlowEngine(
    private val flow: IvrFlow,
    private val handler: FlowActionHandler,
    initialVars: Map<String, String> = emptyMap(),
    private val maxSteps: Int = 200
) {
    private val vars = initialVars.toMutableMap()
    private val path = mutableListOf<String>()
    private val digitsPressed = mutableListOf<String>()

    suspend fun run(): FlowRunResult {
        var currentId: String? = flow.entryNodeId
        var steps = 0

        while (currentId != null) {
            if (steps++ >= maxSteps) {
                return FlowRunResult(path, digitsPressed, vars, FlowEndReason.MAX_STEPS_EXCEEDED)
            }
            val node = flow.nodeById(currentId)
                ?: return FlowRunResult(path, digitsPressed, vars, FlowEndReason.ERROR, "Missing node: $currentId")
            path.add(node.id)

            try {
                currentId = step(node)
            } catch (e: CallEndedException) {
                return FlowRunResult(path, digitsPressed, vars, FlowEndReason.CALL_ENDED, e.message)
            } catch (e: Exception) {
                return FlowRunResult(path, digitsPressed, vars, FlowEndReason.ERROR, e.message)
            }
        }
        return FlowRunResult(path, digitsPressed, vars, FlowEndReason.HANGUP_NODE)
    }

    /** Returns the id of the next node to run, or null if the flow ended. */
    private suspend fun step(node: FlowNode): String? = when (node) {
        is FlowNode.Play -> {
            handler.play(resolve(node.audio))
            node.next
        }

        is FlowNode.Menu -> runMenu(node)

        is FlowNode.CollectDigits -> {
            val digits = handler.collectDigits(resolve(node.prompt), node.numDigits, node.terminatorDigit, node.timeoutMs)
            vars[node.variableName] = digits
            digitsPressed.add(digits)
            node.next
        }

        is FlowNode.Transfer -> {
            handler.transfer(node.destinationNumber, node.mode)
            node.next
        }

        is FlowNode.Voicemail -> {
            val path = handler.recordVoicemail(resolve(node.prompt), node.maxDurationMs, node.playConsentLine)
            vars["voicemail_path"] = path
            node.next
        }

        is FlowNode.Webhook -> {
            handler.callWebhook(node.url, node.method, vars)
            node.next
        }

        is FlowNode.Hangup -> {
            handler.hangup()
            null
        }
    }

    private suspend fun runMenu(node: FlowNode.Menu): String? {
        var retries = 0
        while (true) {
            val digit = handler.waitForDigit(resolve(node.prompt), node.timeoutMs)
            if (digit == null) {
                return node.timeoutNext
            }
            digitsPressed.add(digit.toString())
            val next = node.options[digit.toString()]
            if (next != null) {
                return next
            }
            retries++
            if (retries > node.maxInvalidRetries) {
                return node.invalidNext
            }
            node.invalidPrompt?.let { handler.play(resolve(it)) }
        }
    }

    /** Substitutes {varName} placeholders (e.g. {name}) in TTS prompts with collected flow variables. */
    private fun resolve(audio: AudioSource): AudioSource = when (audio) {
        is AudioSource.Tts -> audio.copy(text = interpolate(audio.text))
        is AudioSource.File -> audio
    }

    private fun interpolate(text: String): String {
        var result = text
        for ((key, value) in vars) {
            result = result.replace("{$key}", value)
        }
        return result
    }
}

/** Thrown by [FlowActionHandler] implementations when the remote party hangs up mid-action. */
class CallEndedException(message: String? = null) : Exception(message)
