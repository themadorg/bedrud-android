package com.bedrud.app.core.meeting.stage

import org.json.JSONObject

object StageWire {
    const val STAGE_DATA_TOPIC = "stage"

    data class MeetingStage(
        val kind: String,
        val ownerIdentity: String,
        val ownerName: String,
        val updatedAt: Long,
    )

    sealed class StageMessage {
        data class Set(val stage: MeetingStage) : StageMessage()
        data class Clear(val ownerIdentity: String, val ts: Long) : StageMessage()
        data class Request(val ts: Long) : StageMessage()
        data class State(val stage: MeetingStage?, val ts: Long) : StageMessage()
    }

    fun encodeStageSet(stage: MeetingStage): ByteArray =
        JSONObject()
            .put("type", "stage_set")
            .put("stage", stage.toJson())
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageClear(ownerIdentity: String, ts: Long): ByteArray =
        JSONObject()
            .put("type", "stage_clear")
            .put("ownerIdentity", ownerIdentity)
            .put("ts", ts)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageRequest(ts: Long): ByteArray =
        JSONObject()
            .put("type", "stage_request")
            .put("ts", ts)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageState(stage: MeetingStage?, ts: Long): ByteArray =
        JSONObject()
            .put("type", "stage_state")
            .put("stage", stage?.toJson())
            .put("ts", ts)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun parse(raw: ByteArray): StageMessage? {
        return try {
            parseJson(JSONObject(String(raw, Charsets.UTF_8)))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(json: JSONObject): StageMessage? {
        return when (json.optString("type")) {
            "stage_set" -> parseMeetingStage(json.optJSONObject("stage"))?.let { StageMessage.Set(it) }
            "stage_clear" -> {
                val ownerIdentity = json.optString("ownerIdentity", "")
                val ts = json.optLong("ts", 0L)
                if (ownerIdentity.isBlank() || ts == 0L) null else StageMessage.Clear(ownerIdentity, ts)
            }
            "stage_request" -> {
                val ts = json.optLong("ts", 0L)
                if (ts == 0L) null else StageMessage.Request(ts)
            }
            "stage_state" -> {
                val ts = json.optLong("ts", 0L)
                if (ts == 0L) return null
                val stageJson = json.opt("stage")
                val stage = when (stageJson) {
                    null, JSONObject.NULL -> null
                    is JSONObject -> parseMeetingStage(stageJson)
                    else -> null
                }
                StageMessage.State(stage, ts)
            }
            else -> null
        }
    }

    private fun parseMeetingStage(json: JSONObject?): MeetingStage? {
        json ?: return null
        val kind = json.optString("kind", "")
        val ownerIdentity = json.optString("ownerIdentity", "")
        val ownerName = json.optString("ownerName", "")
        val updatedAt = json.optLong("updatedAt", 0L)
        if (kind.isBlank() || ownerIdentity.isBlank() || ownerName.isBlank() || updatedAt == 0L) {
            return null
        }
        return when (kind) {
            "screenshare", "whiteboard", "youtube" -> MeetingStage(kind, ownerIdentity, ownerName, updatedAt)
            else -> null
        }
    }

    private fun MeetingStage.toJson(): JSONObject =
        JSONObject()
            .put("kind", kind)
            .put("ownerIdentity", ownerIdentity)
            .put("ownerName", ownerName)
            .put("updatedAt", updatedAt)
}