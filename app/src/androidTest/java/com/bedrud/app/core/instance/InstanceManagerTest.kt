package com.bedrud.app.core.instance

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.instancePrefsName
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.models.Instance
import com.bedrud.app.ui.screens.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Removing a saved server, against the real credential files: what a removal must leave behind is
 * nothing, and that is only visible on the device's own storage.
 */
class InstanceManagerTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private val serverInUse = Instance(serverURL = "https://in-use.example.com", displayName = "In use")
    private val otherServer = Instance(serverURL = "https://other.example.com", displayName = "Other")

    // Store files of this test's own, so a run never touches the servers the installed app keeps.
    private val instancesFile = "test_instances_${serverInUse.id}"
    private val recentsFile = "test_recent_rooms_${serverInUse.id}"

    private lateinit var store: InstanceStore
    private lateinit var recentRooms: RecentRoomsStore
    private lateinit var manager: InstanceManager

    private fun credentialFile(instance: Instance): File =
        File(File(application.applicationInfo.dataDir, "shared_prefs"), "${instancePrefsName(instance.id)}.xml")

    private fun signIn(instance: Instance) {
        AuthManager(application, instance.id).saveTokens("access-token", "refresh-token")
        // The tokens are saved with apply(), which reaches the disk later. An empty commit waits for
        // it, so the file is there before the removal and its absence afterwards means something.
        application.getSharedPreferences(instancePrefsName(instance.id), Context.MODE_PRIVATE).edit().commit()
        check(credentialFile(instance).exists())
    }

    @Before
    fun setUp() {
        store = InstanceStore(application.getSharedPreferences(instancesFile, Context.MODE_PRIVATE))
        recentRooms = RecentRoomsStore(application.getSharedPreferences(recentsFile, Context.MODE_PRIVATE))
        store.addInstance(serverInUse)
        store.addInstance(otherServer)
        store.setActive(serverInUse.id)
        signIn(serverInUse)
        signIn(otherServer)
        manager = InstanceManager(application, store, SettingsStore(application), recentRooms)
    }

    @After
    fun tearDown() {
        listOf(instancesFile, recentsFile, instancePrefsName(serverInUse.id), instancePrefsName(otherServer.id))
            .forEach { application.deleteSharedPreferences(it) }
    }

    @Test
    fun shouldDeleteSignInOfRemovedServerNotInUse() {
        manager.removeInstance(otherServer.id)

        assertFalse("credential file left behind", credentialFile(otherServer).exists())
        assertFalse(AuthManager(application, otherServer.id).isAuthenticated())
    }

    @Test
    fun shouldDeleteSignInOfRemovedServerInUse() {
        manager.removeInstance(serverInUse.id)

        assertFalse("credential file left behind", credentialFile(serverInUse).exists())
        assertFalse(AuthManager(application, serverInUse.id).isAuthenticated())
    }

    @Test
    fun shouldKeepSignInOfServerInUseWhenAnotherServerIsRemoved() {
        manager.removeInstance(otherServer.id)

        assertEquals(true, manager.authManager.value?.isAuthenticated())
    }

    @Test
    fun shouldForgetRecentRoomsOfRemovedServer() {
        recentRooms.add("room-on-other", otherServer.id)
        recentRooms.add("room-in-use", serverInUse.id)

        manager.removeInstance(otherServer.id)

        assertEquals(listOf("room-in-use"), recentRooms.rooms.value.map { it.roomName })
    }

    @Test
    fun shouldKeepClientsOfServerInUseWhenAnotherServerIsRemoved() {
        val roomManager = manager.roomManager.value
        val authManager = manager.authManager.value

        manager.removeInstance(otherServer.id)

        assertSame(roomManager, manager.roomManager.value)
        assertSame(authManager, manager.authManager.value)
    }

    @Test
    fun shouldMoveToNextServerWhenServerInUseIsRemoved() {
        manager.removeInstance(serverInUse.id)

        assertEquals(otherServer.id, store.activeInstanceId.value)
        assertEquals(true, manager.authManager.value?.isAuthenticated())
    }
}
