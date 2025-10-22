package io.github.darkstarworks.dupeTrace.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

object ItemIdUtil {
    private fun key(plugin: JavaPlugin) = NamespacedKey(plugin, "unique_id")

    /**
     * Ensures a UUID is present ONLY for non-stackable items (maxStackSize == 1).
     * Returns the UUID if set/present, or null for stackable items or items without meta.
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

    fun getId(plugin: JavaPlugin, item: ItemStack?): UUID? {
        if (item == null) return null
        if (item.type.maxStackSize != 1) return null
        val meta = item.itemMeta ?: return null
        val value = meta.persistentDataContainer.get(key(plugin), PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }
}
