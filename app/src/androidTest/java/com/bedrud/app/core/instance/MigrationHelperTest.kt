package com.bedrud.app.core.instance

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.core.auth.AuthPrefsKeys
import com.bedrud.app.core.auth.deleteInstancePrefs
import com.bedrud.app.core.auth.instancePrefsName
import com.bedrud.app.core.auth.legacySecurePrefs
import com.bedrud.app.core.auth.secureInstancePrefs
import com.bedrud.app.core.prefs.editBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The names `MigrationHelper` reads and writes. They are written out on purpose: renaming either
 * would leave an upgrading install's sign-in behind, or run the migration again on every install.
 */
private const val MigrationFlagFile = "bedrud_migration"
private const val MigrationDoneKey = "bedrud_migration_v1_done"
private const val SingleServerFile = "bedrud_secure_prefs"

/** The one server the single-server app ever signed in to. */
private const val SingleServerURL = "https://bedrud.com"

private const val AccessToken = "access-token"
private const val RefreshToken = "refresh-token"
private const val UserRecord = """{"id":"user","email":"user@example.com","name":"User"}"""

/**
 * Carrying a sign-in from the single-server app into the per-server layout, against the device's
 * own credential files: the old file is the only copy of that session, so it may go only once the
 * new one is on disk.
 */
class MigrationHelperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // A store file of this test's own, so a run never touches the servers the installed app keeps.
    private val instancesFile = "test_migration_instances"
    private lateinit var store: InstanceStore
    private var flagBefore = false

    private fun sharedPrefsFile(name: String) =
        File(File(context.applicationInfo.dataDir, "shared_prefs"), "$name.xml")

    private fun migrationFlag() = context.getSharedPreferences(MigrationFlagFile, Context.MODE_PRIVATE)

    /** Leaves a sign-in in the single-server app's file, as an install from before servers had. */
    private fun signInToSingleServerApp() {
        legacySecurePrefs(context, SingleServerFile).editBlocking {
            putString(AuthPrefsKeys.ACCESS_TOKEN, AccessToken)
            putString(AuthPrefsKeys.REFRESH_TOKEN, RefreshToken)
            putString(AuthPrefsKeys.USER, UserRecord)
        }
    }

    private fun migrate() = MigrationHelper.migrateIfNeeded(context, store)

    @Before
    fun setUp() {
        flagBefore = migrationFlag().getBoolean(MigrationDoneKey, false)
        migrationFlag().editBlocking { remove(MigrationDoneKey) }
        store = InstanceStore(context.getSharedPreferences(instancesFile, Context.MODE_PRIVATE))
    }

    @After
    fun tearDown() {
        store.instances.value.forEach { deleteInstancePrefs(context, it.id) }
        context.getSharedPreferences(instancesFile, Context.MODE_PRIVATE).editBlocking { clear() }
        context.deleteSharedPreferences(instancesFile)
        context.getSharedPreferences(SingleServerFile, Context.MODE_PRIVATE).editBlocking { clear() }
        context.deleteSharedPreferences(SingleServerFile)
        migrationFlag().editBlocking { putBoolean(MigrationDoneKey, flagBefore) }
    }

    @Test
    fun shouldCarryTheSignInToTheSingleServerAsTheActiveServer() {
        signInToSingleServerApp()

        migrate()

        val server = store.instances.value.single()
        assertEquals(SingleServerURL, server.serverURL)
        assertEquals(server.id, store.activeInstanceId.value)
        val credentials = secureInstancePrefs(context, server.id)
        assertEquals(AccessToken, credentials.getString(AuthPrefsKeys.ACCESS_TOKEN, null))
        assertEquals(RefreshToken, credentials.getString(AuthPrefsKeys.REFRESH_TOKEN, null))
        assertEquals(UserRecord, credentials.getString(AuthPrefsKeys.USER, null))
    }

    /**
     * The old file is deleted before the migration returns, so the new one must be on disk by then:
     * a process killed in between would otherwise have neither, and the user would be signed out.
     */
    @Test
    fun shouldHaveTheNewSignInOnDiskOnceTheOldFileIsGone() {
        signInToSingleServerApp()

        migrate()

        val server = store.instances.value.single()
        assertFalse("old file still there", sharedPrefsFile(SingleServerFile).exists())
        val newFile = sharedPrefsFile(instancePrefsName(server.id))
        assertTrue("new file not on disk yet", newFile.exists())
        assertTrue("access token not on disk yet", newFile.readText().contains(AuthPrefsKeys.ACCESS_TOKEN))
    }

    /** Credentials on disk are no use to a server entry that never got there: nothing would read them. */
    @Test
    fun shouldHaveTheNewServerOnDiskOnceTheOldFileIsGone() {
        signInToSingleServerApp()

        migrate()

        val server = store.instances.value.single()
        assertFalse("old file still there", sharedPrefsFile(SingleServerFile).exists())
        val instances = sharedPrefsFile(instancesFile)
        assertTrue("server list not on disk yet", instances.exists())
        assertTrue("new server not on disk yet", instances.readText().contains(server.id))
    }

    @Test
    fun shouldAddNoServerWhenTheOldAppWasSignedOut() {
        migrate()

        assertTrue(store.instances.value.isEmpty())
    }

    @Test
    fun shouldRunOnlyOnce() {
        signInToSingleServerApp()
        migrate()
        signInToSingleServerApp()

        migrate()

        assertEquals(1, store.instances.value.size)
    }
}
