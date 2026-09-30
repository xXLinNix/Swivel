package io.github.xxlinnix.swivel.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListenerRegistryTest {
    @Test
    fun registeringTwiceKeepsOneEntryWithTheNewState() {
        val registry = ListenerRegistry<String>()
        registry.register("game", uid = 10, KeyCodeStyle.STANDARD, MogaSdk.ACTIVITY_SERVICE_CONNECTED)
        registry.register("game", uid = 10, KeyCodeStyle.STANDARD, MogaSdk.ACTIVITY_RESUME)
        assertEquals(1, registry.all().size)
        assertEquals(MogaSdk.ACTIVITY_RESUME, registry.all().single().activityEvent)
    }

    @Test
    fun aPausedGameStopsReceivingUntilItResumes() {
        val registry = ListenerRegistry<String>()
        registry.register("a", uid = 10, KeyCodeStyle.STANDARD, MogaSdk.ACTIVITY_RESUME)
        registry.register("b", uid = 11, KeyCodeStyle.LEGACY, MogaSdk.ACTIVITY_RESUME)
        registry.setActivityEvent(uid = 10, MogaSdk.ACTIVITY_PAUSE)
        assertEquals(listOf("b"), registry.receiving().map { it.listener })
        registry.setActivityEvent(uid = 10, MogaSdk.ACTIVITY_RESUME)
        assertEquals(listOf("a", "b"), registry.receiving().map { it.listener })
    }

    @Test
    fun unregisterAndCounts() {
        val registry = ListenerRegistry<String>()
        registry.register("a1", uid = 10, KeyCodeStyle.STANDARD, 0)
        registry.register("a2", uid = 10, KeyCodeStyle.STANDARD, 0)
        registry.register("b", uid = 11, KeyCodeStyle.LEGACY, 0)
        assertEquals(2, registry.gameCount())
        assertEquals(KeyCodeStyle.LEGACY, registry.styleOf(11))
        assertEquals(KeyCodeStyle.STANDARD, registry.styleOf(99))
        registry.unregister("b")
        registry.unregister("a1")
        registry.unregister("a2")
        assertTrue(registry.all().isEmpty())
    }
}
