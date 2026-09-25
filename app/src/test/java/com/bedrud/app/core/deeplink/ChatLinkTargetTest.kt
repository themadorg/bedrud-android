package com.bedrud.app.core.deeplink

import com.bedrud.app.models.Instance
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLinkTargetTest {

    private val activeServer = Instance(id = "active", serverURL = "https://bedrud.xyz/", displayName = "Bedrud")
    private val otherServer = Instance(id = "other", serverURL = "https://demo.bedrud.xyz/", displayName = "Demo")
    private val servers = listOf(activeServer, otherServer)

    private fun resolve(url: String): ChatLinkTarget = resolveChatLink(
        url = url,
        servers = servers,
        activeServerId = activeServer.id,
        currentRoomName = CurrentRoom,
    )

    @Test
    fun `should open a room link on a server nobody added as a page`() {
        assertEquals(ChatLinkTarget.Page, resolve("https://example.com/m/$OtherRoom"))
    }

    @Test
    fun `should open a link to an added server that names no room as a page`() {
        assertEquals(ChatLinkTarget.Page, resolve("https://bedrud.xyz/about"))
    }

    @Test
    fun `should resolve a link to the room this call is in as the current room`() {
        assertEquals(ChatLinkTarget.CurrentRoom, resolve("https://bedrud.xyz/m/$CurrentRoom"))
    }

    @Test
    fun `should resolve another room on the active server without a server switch`() {
        assertEquals(
            ChatLinkTarget.Room(OtherRoom, activeServer, switchesServer = false),
            resolve("https://bedrud.xyz/m/$OtherRoom"),
        )
    }

    /** Room names are only unique per server, so the same name elsewhere is a different room. */
    @Test
    fun `should resolve the current room's name on another added server as a room behind a switch`() {
        assertEquals(
            ChatLinkTarget.Room(CurrentRoom, otherServer, switchesServer = true),
            resolve("https://demo.bedrud.xyz/m/$CurrentRoom"),
        )
    }

    @Test
    fun `should resolve a room link written without a scheme`() {
        assertEquals(
            ChatLinkTarget.Room(OtherRoom, activeServer, switchesServer = false),
            resolve("bedrud.xyz/c/$OtherRoom"),
        )
    }

    @Test
    fun `should match the server whatever the case of its host`() {
        assertEquals(
            ChatLinkTarget.Room(OtherRoom, activeServer, switchesServer = false),
            resolve("https://Bedrud.XYZ/m/$OtherRoom"),
        )
    }

    private companion object {
        const val CurrentRoom = "obd-hzpi-dfv"
        const val OtherRoom = "oqc-qpka-bvw"
    }
}
