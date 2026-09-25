package com.bedrud.app.core.deeplink

import com.bedrud.app.models.Instance

/** Where a link somebody sent in the chat leads, decided before anything is opened. */
sealed interface ChatLinkTarget {
    /**
     * Anything that is not a room on a server the reader has added. A link to somebody else's site
     * that happens to carry an `/m/` path is theirs, not this app's to swallow.
     */
    data object Page : ChatLinkTarget

    /** The room this call is already in, on the server it is in. */
    data object CurrentRoom : ChatLinkTarget

    /**
     * A room to join on [server], one of the reader's own. [switchesServer] is true when that is not
     * the server this call is on, so following the link also changes which server the app talks to.
     */
    data class Room(val roomName: String, val server: Instance, val switchesServer: Boolean) : ChatLinkTarget
}

/**
 * Resolves a chat link against the servers the reader has added.
 *
 * A link matches a server by its whole base URL, the way a pasted link on the dashboard does, so
 * `http://` and a different port are different servers. The current room is a name *and* a server:
 * room names are only unique per server, so the same name on another server is another room.
 */
fun resolveChatLink(
    url: String,
    servers: List<Instance>,
    activeServerId: String?,
    currentRoomName: String,
): ChatLinkTarget {
    val link = BedrudURLParser.parse(url) ?: return ChatLinkTarget.Page
    val server = servers.firstOrNull { BedrudURLParser.matchesServer(it.serverURL, link.serverBaseURL) }
        ?: return ChatLinkTarget.Page
    val switchesServer = server.id != activeServerId

    return if (!switchesServer && link.roomName == currentRoomName) {
        ChatLinkTarget.CurrentRoom
    } else {
        ChatLinkTarget.Room(link.roomName, server, switchesServer)
    }
}
