package io.github.xxlinnix.swivel.core.bridge

/**
 * The games listening to the bridge. [T] is whatever identifies a listener; on Android,
 * its Binder. A game registers each listener with its activity state, and later updates
 * the state for all its listeners at once by uid.
 */
class ListenerRegistry<T : Any> {
    data class Entry<T>(val listener: T, val uid: Int, val style: KeyCodeStyle, val activityEvent: Int)

    private val entries = mutableListOf<Entry<T>>()

    @Synchronized
    fun register(listener: T, uid: Int, style: KeyCodeStyle, activityEvent: Int) {
        entries.removeAll { it.listener == listener }
        entries += Entry(listener, uid, style, activityEvent)
    }

    @Synchronized
    fun unregister(listener: T) {
        entries.removeAll { it.listener == listener }
    }

    @Synchronized
    fun setActivityEvent(uid: Int, activityEvent: Int) {
        entries.replaceAll { if (it.uid == uid) it.copy(activityEvent = activityEvent) else it }
    }

    /** The key-code style a uid registered with, for its getKeyCode calls. */
    @Synchronized
    fun styleOf(uid: Int): KeyCodeStyle = entries.lastOrNull { it.uid == uid }?.style ?: KeyCodeStyle.STANDARD

    /** Listeners that should get input now. */
    @Synchronized
    fun receiving(): List<Entry<T>> = entries.filter { MogaSdkMapping.receivesEvents(it.activityEvent) }

    @Synchronized
    fun all(): List<Entry<T>> = entries.toList()

    @Synchronized
    fun gameCount(): Int = entries.map { it.uid }.distinct().size
}
