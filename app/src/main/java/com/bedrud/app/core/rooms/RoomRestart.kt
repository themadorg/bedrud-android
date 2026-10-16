package com.bedrud.app.core.rooms

import com.bedrud.app.models.CreateRoomRequest
import com.bedrud.app.models.JoinRoomResponse
import com.bedrud.app.models.UserRoomResponse

/**
 * How the server answers its owner joining a room that has ended: a 200 instead of a refusal, so
 * the app can offer to start it again under the same name. Anyone else gets a 410.
 */
private const val ENDED_OWN_ROOM_STATUS = "archived_owned"

/**
 * Whether a join was answered with a room that has ended and is the joiner's own to start again.
 *
 * A room ends by itself a few minutes after the last person leaves, unless it is persistent, so
 * this is what opening almost any of your rooms from the dashboard gets once its call is over.
 */
fun isEndedOwnRoom(answer: JoinRoomResponse): Boolean = answer.status == ENDED_OWN_ROOM_STATUS

/**
 * The request that starts an [ended] room again: a new room under the same name, so its link still
 * leads to it, with the same mode and settings.
 *
 * The answer that reported it ended carries no public setting or participant limit, so both come
 * from the owner's own room list. Left out, the server would make the room private, and nobody
 * else could join a room that was public before. A room missing from the list leaves them to the
 * server rather than guessing; a deleted room sharing the name is not the one that ended.
 */
fun restartRequest(ended: JoinRoomResponse, ownRooms: List<UserRoomResponse>): CreateRoomRequest {
    val listed = ownRooms.firstOrNull { room -> room.name == ended.name && room.deletedAt == null }
    return CreateRoomRequest(
        name = ended.name,
        maxParticipants = listed?.maxParticipants,
        isPublic = listed?.isPublic,
        mode = ended.mode,
        settings = ended.settings,
    )
}
