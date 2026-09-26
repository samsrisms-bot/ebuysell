package com.simivr.app.flow.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Gson only serializes sealed classes as their concrete runtime type would suggest via
 * reflection, which loses the "kind" needed to deserialize back into the right subclass.
 * These adapters add an explicit "kind" discriminator field so [IvrFlow] round-trips
 * through JSON (Room stores flows as a single JSON TEXT column).
 */
object FlowJson {

    val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(FlowNode::class.java, FlowNodeAdapter())
            .registerTypeAdapter(AudioSource::class.java, AudioSourceAdapter())
            .create()
    }

    fun toJson(flow: IvrFlow): String = gson.toJson(flow)

    fun fromJson(json: String): IvrFlow = gson.fromJson(json, IvrFlow::class.java)
}

private class AudioSourceAdapter : JsonSerializer<AudioSource>, JsonDeserializer<AudioSource> {
    override fun serialize(src: AudioSource, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = context.serialize(src).asJsonObject
        obj.addProperty("kind", if (src is AudioSource.Tts) "tts" else "file")
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AudioSource {
        val obj = json.asJsonObject
        return when (obj.get("kind")?.asString) {
            "tts" -> context.deserialize(obj, AudioSource.Tts::class.java)
            else -> context.deserialize(obj, AudioSource.File::class.java)
        }
    }
}

private class FlowNodeAdapter : JsonSerializer<FlowNode>, JsonDeserializer<FlowNode> {
    override fun serialize(src: FlowNode, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val kind = when (src) {
            is FlowNode.Play -> "play"
            is FlowNode.Menu -> "menu"
            is FlowNode.CollectDigits -> "collect"
            is FlowNode.Transfer -> "transfer"
            is FlowNode.Voicemail -> "voicemail"
            is FlowNode.Webhook -> "webhook"
            is FlowNode.Hangup -> "hangup"
        }
        val obj = context.serialize(src, src.javaClass).asJsonObject
        obj.addProperty("kind", kind)
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FlowNode {
        val obj: JsonObject = json.asJsonObject
        return when (obj.get("kind")?.asString) {
            "play" -> context.deserialize(obj, FlowNode.Play::class.java)
            "menu" -> context.deserialize(obj, FlowNode.Menu::class.java)
            "collect" -> context.deserialize(obj, FlowNode.CollectDigits::class.java)
            "transfer" -> context.deserialize(obj, FlowNode.Transfer::class.java)
            "voicemail" -> context.deserialize(obj, FlowNode.Voicemail::class.java)
            "webhook" -> context.deserialize(obj, FlowNode.Webhook::class.java)
            "hangup" -> context.deserialize(obj, FlowNode.Hangup::class.java)
            else -> error("Unknown flow node kind: ${obj.get("kind")}")
        }
    }
}
