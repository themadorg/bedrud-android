package com.bedrud.app.core.instance

import com.bedrud.app.models.Instance
import com.bedrud.app.testutil.InMemorySharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InstanceStoreTest {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var store: InstanceStore

    @Before
    fun setUp() {
        prefs = InMemorySharedPreferences()
        store = InstanceStore(prefs)
    }

    @Test
    fun `init loads empty list when no data`() {
        assertTrue(store.instances.value.isEmpty())
        assertNull(store.activeInstanceId.value)
        assertNull(store.activeInstance)
    }

    @Test
    fun `addInstance persists and auto-sets first as active`() {
        val instance = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        store.addInstance(instance)

        assertEquals(1, store.instances.value.size)
        assertEquals("i1", store.instances.value[0].id)
        assertEquals("i1", store.activeInstanceId.value)
        assertEquals(instance, store.activeInstance)
    }

    @Test
    fun `addInstance second does not change active`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)

        assertEquals(2, store.instances.value.size)
        assertEquals("i1", store.activeInstanceId.value)
    }

    @Test
    fun `removeInstance updates active to first remaining`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)
        store.setActive("i1")

        store.removeInstance("i1")

        assertEquals(1, store.instances.value.size)
        assertEquals("i2", store.activeInstanceId.value)
    }

    @Test
    fun `removeInstance last instance sets active to null`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        store.addInstance(i1)

        store.removeInstance("i1")

        assertTrue(store.instances.value.isEmpty())
        assertNull(store.activeInstanceId.value)
        assertNull(store.activeInstance)
    }

    @Test
    fun `should name the first other saved server as the one taking over from a removed server`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        val i3 = Instance(id = "i3", serverURL = "https://c.com", displayName = "C")
        store.addInstance(i1)
        store.addInstance(i2)
        store.addInstance(i3)

        assertEquals(i2, store.serverAfterRemoving("i1"))
        assertEquals(i1, store.serverAfterRemoving("i2"))
        assertEquals(i1, store.serverAfterRemoving("i3"))
    }

    @Test
    fun `should name no server as taking over from the only saved server`() {
        store.addInstance(Instance(id = "i1", serverURL = "https://a.com", displayName = "A"))

        assertNull(store.serverAfterRemoving("i1"))
    }

    @Test
    fun `should make the server it names active when the active server is removed`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        val i3 = Instance(id = "i3", serverURL = "https://c.com", displayName = "C")
        store.addInstance(i1)
        store.addInstance(i2)
        store.addInstance(i3)
        store.setActive("i2")
        val named = store.serverAfterRemoving("i2")

        store.removeInstance("i2")

        assertEquals(named, store.activeInstance)
    }

    @Test
    fun `should keep the active server when another server is removed`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)
        store.setActive("i2")

        store.removeInstance("i1")

        assertEquals(i2, store.activeInstance)
    }

    @Test
    fun `setActive with valid id updates activeInstanceId`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)

        store.setActive("i2")
        assertEquals("i2", store.activeInstanceId.value)
        assertEquals(i2, store.activeInstance)
    }

    @Test
    fun `setActive with invalid id is no-op`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        store.addInstance(i1)

        store.setActive("nonexistent")
        assertEquals("i1", store.activeInstanceId.value)
    }

    @Test
    fun `activeInstance returns correct instance`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)
        store.setActive("i2")

        val active = store.activeInstance
        assertNotNull(active)
        assertEquals("i2", active!!.id)
        assertEquals("B", active.displayName)
    }

    @Test
    fun `persistence - new store from same prefs preserves data`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "A")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "B")
        store.addInstance(i1)
        store.addInstance(i2)
        store.setActive("i2")

        // Create a new store with the same prefs
        val store2 = InstanceStore(prefs)
        assertEquals(2, store2.instances.value.size)
        assertEquals("i2", store2.activeInstanceId.value)
        assertEquals("B", store2.activeInstance?.displayName)
    }

    @Test
    fun `updateDisplayName renames matching instance and persists`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "a.com")
        val i2 = Instance(id = "i2", serverURL = "https://b.com", displayName = "b.com")
        store.addInstance(i1)
        store.addInstance(i2)

        store.updateDisplayName("i2", "Acme Meet")

        assertEquals("a.com", store.instances.value.first { it.id == "i1" }.displayName)
        assertEquals("Acme Meet", store.instances.value.first { it.id == "i2" }.displayName)
        // survives a reload from the same prefs
        val reloaded = InstanceStore(prefs)
        assertEquals("Acme Meet", reloaded.instances.value.first { it.id == "i2" }.displayName)
    }

    @Test
    fun `updateDisplayName with unknown id is no-op`() {
        val i1 = Instance(id = "i1", serverURL = "https://a.com", displayName = "a.com")
        store.addInstance(i1)

        store.updateDisplayName("nonexistent", "Whatever")

        assertEquals("a.com", store.instances.value.first { it.id == "i1" }.displayName)
    }
}
