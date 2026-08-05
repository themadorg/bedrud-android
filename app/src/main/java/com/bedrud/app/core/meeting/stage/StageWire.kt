package com.bedrud.app.core.meeting.stage

import org.json.JSONObject

object StageWire {
    const val STAGE_DATA_TOPIC = "stage"

    // Stage kinds — the "who's on stage" content type, shared with RoomManager and the meeting UI.
    const val KIND_SCREENSHARE = "screenshare"
    const val KIND_WHITEBOARD = "whiteboard"
    const val KIND_YOUTUBE = "youtube"

    // Wire message types carried in the payload's "type" field.
    private const val TYPE_STAGE_SET = "stage_set"
    private const val TYPE_STAGE_CLEAR = "stage_clear"
    private const val TYPE_STAGE_REQUEST = "stage_request"
    private const val TYPE_STAGE_STATE = "stage_state"

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
            .put("type", TYPE_STAGE_SET)
            .put("stage", stage.toJson())
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageClear(ownerIdentity: String, ts: Long): ByteArray =
        JSONObject()
            .put("type", TYPE_STAGE_CLEAR)
            .put("ownerIdentity", ownerIdentity)
            .put("ts", ts)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageRequest(ts: Long): ByteArray =
        JSONObject()
            .put("type", TYPE_STAGE_REQUEST)
            .put("ts", ts)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeStageState(stage: MeetingStage?, ts: Long): ByteArray =
        JSONObject()
            .put("type", TYPE_STAGE_STATE)
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
            TYPE_STAGE_SET -> parseMeetingStage(json.optJSONObject("stage"))?.let { StageMessage.Set(it) }
            TYPE_STAGE_CLEAR -> {
                val ownerIdentity = json.optString("ownerIdentity", "")
                val ts = json.optLong("ts", 0L)
                if (ownerIdentity.isBlank() || ts == 0L) null else StageMessage.Clear(ownerIdentity, ts)
            }
            TYPE_STAGE_REQUEST -> {
                val ts = json.optLong("ts", 0L)
                if (ts == 0L) null else StageMessage.Request(ts)
            }
            TYPE_STAGE_STATE -> {
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
            KIND_SCREENSHARE, KIND_WHITEBOARD, KIND_YOUTUBE ->
                MeetingStage(kind, ownerIdentity, ownerName, updatedAt)
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