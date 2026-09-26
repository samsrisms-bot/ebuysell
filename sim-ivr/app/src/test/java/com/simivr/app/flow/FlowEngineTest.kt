package com.simivr.app.flow

import com.google.common.truth.Truth.assertThat
import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.FlowNode
import com.simivr.app.flow.model.IvrFlow
import com.simivr.app.flow.model.TransferMode
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Records everything played/collected so assertions can inspect exact call order and content. */
private class FakeFlowActionHandler(
    private val digitScript: MutableList<Char?> = mutableListOf(),
    private val collectScript: MutableList<String> = mutableListOf()
) : FlowActionHandler {
    val playedTexts = mutableListOf<String>()
    val transfers = mutableListOf<Pair<String, TransferMode>>()
    val webhooksCalled = mutableListOf<String>()
    var hungUp = false

    override suspend fun play(audio: AudioSource) {
        playedTexts.add((audio as AudioSource.Tts).text)
    }

    override suspend fun collectDigits(prompt: AudioSource, numDigits: Int, terminator: Char?, timeoutMs: Long): String {
        play(prompt)
        return if (collectScript.isNotEmpty()) collectScript.removeAt(0) else ""
    }

    override suspend fun waitForDigit(prompt: AudioSource, timeoutMs: Long): Char? {
        play(prompt)
        return if (digitScript.isNotEmpty()) digitScript.removeAt(0) else null
    }

    override suspend fun transfer(number: String, mode: TransferMode) {
        transfers.add(number to mode)
    }

    override suspend fun recordVoicemail(prompt: AudioSource, maxDurationMs: Long, playConsentLine: Boolean): String {
        play(prompt)
        return "/fake/voicemail.wav"
    }

    override suspend fun callWebhook(url: String, method: String, vars: Map<String, String>): Boolean {
        webhooksCalled.add(url)
        return true
    }

    override suspend fun hangup() {
        hungUp = true
    }
}

class FlowEngineTest {

    private fun sampleFlow(): IvrFlow = IvrFlow(
        id = "test-flow",
        name = "Test",
        entryNodeId = "greeting",
        nodes = listOf(
            FlowNode.Play("greeting", AudioSource.Tts("Hello {name}"), "menu"),
            FlowNode.Menu(
                id = "menu",
                prompt = AudioSource.Tts("Press 1 for sales, 2 for support"),
                options = mapOf("1" to "sales", "2" to "support"),
                timeoutMs = 5000,
                timeoutNext = "hangup",
                invalidPrompt = AudioSource.Tts("Invalid"),
                maxInvalidRetries = 1,
                invalidNext = "hangup"
            ),
            FlowNode.Play("sales", AudioSource.Tts("Connecting to sales"), "transfer_sales"),
            FlowNode.Transfer("transfer_sales", "+911234567890", TransferMode.CONFERENCE_MERGE, "hangup"),
            FlowNode.Play("support", AudioSource.Tts("Connecting to support"), "webhook_support"),
            FlowNode.Webhook("webhook_support", "https://example.com/support", "POST", "hangup"),
            FlowNode.Hangup("hangup")
        )
    )

    @Test
    fun `follows the happy path and substitutes tts variables`() = runTest {
        val handler = FakeFlowActionHandler(digitScript = mutableListOf('1'))
        val result = FlowEngine(sampleFlow(), handler, initialVars = mapOf("name" to "Asha")).run()

        assertThat(handler.playedTexts.first()).isEqualTo("Hello Asha")
        assertThat(handler.transfers).containsExactly("+911234567890" to TransferMode.CONFERENCE_MERGE)
        assertThat(handler.hungUp).isTrue()
        assertThat(result.endReason).isEqualTo(FlowEndReason.HANGUP_NODE)
        assertThat(result.path).containsExactly("greeting", "menu", "sales", "transfer_sales", "hangup").inOrder()
        assertThat(result.digitsPressed).containsExactly("1")
    }

    @Test
    fun `routes digit 2 to the support webhook branch`() = runTest {
        val handler = FakeFlowActionHandler(digitScript = mutableListOf('2'))
        val result = FlowEngine(sampleFlow(), handler).run()

        assertThat(handler.webhooksCalled).containsExactly("https://example.com/support")
        assertThat(result.path).contains("webhook_support")
    }

    @Test
    fun `falls back to timeoutNext when no digit is pressed`() = runTest {
        val handler = FakeFlowActionHandler(digitScript = mutableListOf(null))
        val result = FlowEngine(sampleFlow(), handler).run()

        assertThat(handler.hungUp).isTrue()
        assertThat(result.path).containsExactly("greeting", "menu", "hangup").inOrder()
        assertThat(result.digitsPressed).isEmpty()
    }

    @Test
    fun `retries once on an invalid digit before giving up`() = runTest {
        val handler = FakeFlowActionHandler(digitScript = mutableListOf('9', '1'))
        val result = FlowEngine(sampleFlow(), handler).run()

        // '9' is invalid (maxInvalidRetries = 1, so one retry is allowed), then '1' succeeds.
        assertThat(handler.playedTexts).contains("Invalid")
        assertThat(result.path).contains("sales")
    }

    @Test
    fun `gives up after exceeding max invalid retries and follows invalidNext`() = runTest {
        val handler = FakeFlowActionHandler(digitScript = mutableListOf('9', '9'))
        val result = FlowEngine(sampleFlow(), handler).run()

        assertThat(handler.hungUp).isTrue()
        assertThat(result.path).containsExactly("greeting", "menu", "hangup").inOrder()
    }

    @Test
    fun `collect digits node stores the value as a flow variable available to later prompts`() = runTest {
        val flow = IvrFlow(
            id = "collect-flow",
            name = "Collect",
            entryNodeId = "collect",
            nodes = listOf(
                FlowNode.CollectDigits("collect", AudioSource.Tts("Enter your code"), 4, '#', 8000, "code", "confirm"),
                FlowNode.Play("confirm", AudioSource.Tts("You entered {code}"), "hangup"),
                FlowNode.Hangup("hangup")
            )
        )
        val handler = FakeFlowActionHandler(collectScript = mutableListOf("1234"))
        val result = FlowEngine(flow, handler).run()

        assertThat(handler.playedTexts).contains("You entered 1234")
        assertThat(result.vars["code"]).isEqualTo("1234")
    }

    @Test
    fun `stops after maxSteps to avoid an infinite loop in a malformed flow`() = runTest {
        val flow = IvrFlow(
            id = "loop-flow",
            name = "Loop",
            entryNodeId = "a",
            nodes = listOf(
                FlowNode.Play("a", AudioSource.Tts("A"), "b"),
                FlowNode.Play("b", AudioSource.Tts("B"), "a")
            )
        )
        val handler = FakeFlowActionHandler()
        val result = FlowEngine(flow, handler, maxSteps = 10).run()

        assertThat(result.endReason).isEqualTo(FlowEndReason.MAX_STEPS_EXCEEDED)
        assertThat(result.path.size).isEqualTo(10)
    }

    @Test
    fun `reports an error when a referenced node id is missing`() = runTest {
        val flow = IvrFlow(
            id = "broken-flow",
            name = "Broken",
            entryNodeId = "start",
            nodes = listOf(FlowNode.Play("start", AudioSource.Tts("Hi"), "does_not_exist"))
        )
        val result = FlowEngine(flow, FakeFlowActionHandler()).run()

        assertThat(result.endReason).isEqualTo(FlowEndReason.ERROR)
        assertThat(result.error).contains("does_not_exist")
    }
}
