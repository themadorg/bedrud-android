package com.bedrud.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How [MainProjects] splits the generated library list into the projects shown first and the rest.
 *
 * Library ids stand in for the libraries themselves: AboutLibraries' classes are built for a newer
 * Java than the unit tests run on, so the split is exercised on its ids alone.
 */
class MainProjectsTest {

    private fun split(libraries: List<String>) = MainProjects.split(libraries) { it }

    @Test
    fun `should put the main projects first, in their own order`() {
        val result = split(listOf(Okhttp, Vazirmatn, LiveKit))

        assertEquals(listOf(LiveKit, Vazirmatn), result.main)
    }

    @Test
    fun `should keep every other library, in the order it arrived`() {
        val result = split(listOf(Retrofit, LiveKit, Okhttp))

        assertEquals(listOf(Retrofit, Okhttp), result.others)
    }

    @Test
    fun `should leave out a main project the build does not ship`() {
        val result = split(listOf(LiveKit, Okhttp))

        assertEquals(listOf(LiveKit), result.main)
    }

    private companion object {
        const val LiveKit = "io.livekit:livekit-android"
        const val Vazirmatn = "font:vazirmatn"
        const val Okhttp = "com.squareup.okhttp3:okhttp"
        const val Retrofit = "com.squareup.retrofit2:retrofit"
    }
}
