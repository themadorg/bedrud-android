package com.bedrud.app.core.call

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The address a call is placed under, which is how the room's name reaches the telecom call and
 * comes back out of it. Instrumented, because it is Android's own `Uri`, which a local unit test has
 * no implementation of.
 */
class CallConnectionServiceTest {

    private fun roundTrip(roomName: String) =
        CallConnectionService.parseRoomName(CallConnectionService.roomUri(roomName))

    @Test
    fun shouldCarryAPlainRoomNameThroughItsAddress() {
        assertEquals("weekly-sync", roundTrip("weekly-sync"))
    }

    /** A name is free text: spaces, a slash, or what a URL treats as its own all have to survive. */
    @Test
    fun shouldCarryARoomNameWithSpacesAndSymbolsThroughItsAddress() {
        assertEquals("team sync", roundTrip("team sync"))
        assertEquals("design/review", roundTrip("design/review"))
        assertEquals("50% off & more?#1", roundTrip("50% off & more?#1"))
    }

    /** The address carries the name encoded once, so it must be decoded once and no more. */
    @Test
    fun shouldCarryARoomNameThatSpellsOutAnEscapeThroughItsAddress() {
        assertEquals("a%20b", roundTrip("a%20b"))
    }

    @Test
    fun shouldCarryARoomNameInAnotherScriptThroughItsAddress() {
        assertEquals("جلسه هفتگی", roundTrip("جلسه هفتگی"))
    }

    @Test
    fun shouldPlaceACallUnderTheAppsOwnScheme() {
        assertEquals(CallConnectionService.SCHEME, CallConnectionService.roomUri("weekly-sync").scheme)
    }

    /** A call placed by the system's own dialer arrives with a phone number for its address. */
    @Test
    fun shouldReadAPhoneNumberAddressAsTheName() {
        assertEquals("12345", CallConnectionService.parseRoomName(Uri.parse("tel:12345")))
    }

    @Test
    fun shouldReadTheLastPartOfAnyOtherAddress() {
        assertEquals("standup", CallConnectionService.parseRoomName(Uri.parse("https://bedrud.example/rooms/standup")))
    }

    /** No name means the connection falls back to its default label rather than a blank one. */
    @Test
    fun shouldFindNoNameInAMissingOrEmptyAddress() {
        assertNull(CallConnectionService.parseRoomName(null))
        assertNull(CallConnectionService.parseRoomName(Uri.parse("${CallConnectionService.SCHEME}://room/")))
        assertNull(CallConnectionService.parseRoomName(Uri.parse("tel:")))
    }
}
