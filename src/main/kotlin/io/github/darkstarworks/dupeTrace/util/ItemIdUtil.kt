package io.github.darkstarworks.dupeTrace.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * Utility object for managing unique IDs on non-stackable items.
 *
 * This object provides methods to assign and retrieve UUIDs from items using
 * Bukkit's PersistentDataContainer API. Only non-stackable items (maxStackSize == 1)
 * are eligible for UUID tracking.
 */
object ItemIdUtil {
    private fun key(plugin: JavaPlugin) = NamespacedKey(plugin, "unique_id")

    /**
     * Ensures a UUID is present on the given item if it's non-stackable.
     *
     * Only non-stackable items (maxStackSize == 1) are assigned UUIDs. If the item
     * already has a valid UUID, it is returned. Otherwise, a new UUID is generated,
     * stored in the item's PersistentDataContainer, and returned.
     *
     * @param plugin The plugin instance for creating the NamespacedKey
     * @param item The ItemStack to tag with a UUID (or null)
     * @return The UUID assigned to this item, or null if the item is stackable/null/without meta
     */
    fun ensureUniqueId(plugin: JavaPlugin, item: ItemStack?): UUID? {
        if (item == null) return null
        // Only non-stackable resources should get UUIDs
        if (item.type.maxStackSize != 1) return null
        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer
        val existing = container.get(key(plugin), PersistentDataType.STRING)
        val id = if (existing.isNullOrBlank()) UUID.randomUUID() else runCatching { UUID.fromString(existing) }.getOrNull() ?: UUID.randomUUID()
        container.set(key(plugin), PersistentDataType.STRING, id.toString())
        item.itemMeta = meta
        return id
    }

    /**
     * Retrieves the UUID from an item without modifying it.
     *
     * @param plugin The plugin instance for creating the NamespacedKey
     * @param item The ItemStack to check for a UUID (or null)
     * @return The UUID if present, or null if not found/item is stackable/null/without meta
     */
    fun getId(plugin: JavaPlugin, item: ItemStack?): UUID? {
        if (item == null) return null
        if (item.type.maxStackSize != 1) return null
        val meta = item.itemMeta ?: return null
        val value = meta.persistentDataContainer.get(key(plugin), PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }
}
