package io.github.darkstarworks.dupeTrace.webhook

import org.bukkit.plugin.java.JavaPlugin
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Customizable Discord webhook integration for sending duplicate alerts.
 *
 * Supports:
 * - Custom username and avatar
 * - Configurable embed appearance (colors, fields, footer, images)
 * - Placeholder templates for dynamic content
 * - Role and user mentions
 * - Rate limiting with queuing
 *
 * Author: darkstarworks | https://github.com/darkstarworks
 * Donate: https://ko-fi/darkstarworks
 */
class DiscordWebhook(private val plugin: JavaPlugin) {

    // Alert data class for queuing
    data class AlertData(
        val itemUUID: String,
        val playerName: String,
        val itemType: String,
        val location: String,
        val tags: Set<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Rate limiting state
    private val alertQueue = ConcurrentLinkedQueue<AlertData>()
    private val sentThisMinute = AtomicInteger(0)
    private val minuteStartTime = AtomicLong(System.currentTimeMillis())
    private var queueProcessorRunning = false

    /**
     * Check if Discord webhook is enabled and configured.
     */
    fun isEnabled(): Boolean {
        return plugin.config.getBoolean("discord.enabled", false) &&
                !plugin.config.getString("discord.webhook-url").isNullOrBlank()
    }

    /**
     * Send a duplicate detection alert to Discord.
     */
    fun sendDuplicateAlert(
        itemUUID: String,
        playerName: String,
        itemType: String,
        location: String,
        tags: Set<String> = emptySet()
    ) {
        if (!isEnabled()) return

        val alert = AlertData(itemUUID, playerName, itemType, location, tags)

        if (plugin.config.getBoolean("discord.rate-limit.enabled", true)) {
            queueAlert(alert)
        } else {
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                sendAlertNow(alert)
            })
        }
    }

    private fun queueAlert(alert: AlertData) {
        val maxQueueSize = plugin.config.getInt("discord.rate-limit.queue-max-size", 100)

        // If queue is full, drop oldest
        while (alertQueue.size >= maxQueueSize) {
            alertQueue.poll()
            plugin.logger.warning("[Discord] Alert queue full, dropping oldest alert")
        }

        alertQueue.offer(alert)
        startQueueProcessor()
    }

    @Synchronized
    private fun startQueueProcessor() {
        if (queueProcessorRunning) return
        queueProcessorRunning = true

        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            processQueue()
        }, 0L, 20L) // Check every second
    }

    private fun processQueue() {
        if (alertQueue.isEmpty()) return

        val now = System.currentTimeMillis()
        val maxPerMinute = plugin.config.getInt("discord.rate-limit.max-per-minute", 30)

        // Reset counter if a minute has passed
        if (now - minuteStartTime.get() >= 60_000) {
            minuteStartTime.set(now)
            sentThisMinute.set(0)
        }

        // Send alerts if under rate limit
        while (alertQueue.isNotEmpty() && sentThisMinute.get() < maxPerMinute) {
            val alert = alertQueue.poll() ?: break
            try {
                sendAlertNow(alert)
                sentThisMinute.incrementAndGet()
            } catch (e: Exception) {
                plugin.logger.warning("[Discord] Failed to send queued alert: ${e.message}")
            }
        }
    }

    private fun sendAlertNow(alert: AlertData) {
        try {
            val jsonPayload = buildAlertPayload(alert)
            val webhookUrl = plugin.config.getString("discord.webhook-url") ?: return
            sendWebhook(webhookUrl, jsonPayload)
        } catch (e: Exception) {
            plugin.logger.warning("[Discord] Failed to send alert: ${e.message}")
        }
    }

    private fun buildAlertPayload(alert: AlertData): String {
        val cfg = plugin.config
        val shortUuid = alert.itemUUID.take(8)
        val tagSuffix = if (alert.tags.isNotEmpty()) " [${alert.tags.joinToString(",")}]" else ""

        // Placeholders map
        val placeholders = mapOf(
            "{player}" to alert.playerName,
            "{item_type}" to alert.itemType,
            "{uuid_short}" to shortUuid,
            "{uuid_full}" to alert.itemUUID,
            "{location}" to alert.location,
            "{tags}" to tagSuffix,
            "{version}" to plugin.pluginMeta.version
        )

        // Bot appearance
        val username = cfg.getString("discord.username", "DupeTrace") ?: "DupeTrace"
        val avatarUrl = cfg.getString("discord.avatar-url", "") ?: ""

        // Embed settings
        val colorHex = cfg.getString("discord.embed.color", "#FF0000") ?: "#FF0000"
        val colorDecimal = parseHexColor(colorHex)

        val titleTemplate = cfg.getString("discord.embed.title", "🚨 Duplicate Item Detected") ?: "🚨 Duplicate Item Detected"
        val title = applyPlaceholders(titleTemplate + tagSuffix, placeholders)

        val descriptionTemplate = cfg.getString("discord.embed.description", "") ?: ""
        val description = applyPlaceholders(descriptionTemplate, placeholders)

        // Field visibility
        val showPlayer = cfg.getBoolean("discord.embed.show-player", true)
        val showItemType = cfg.getBoolean("discord.embed.show-item-type", true)
        val showUuid = cfg.getBoolean("discord.embed.show-item-uuid", true)
        val showFullUuid = cfg.getBoolean("discord.embed.show-full-uuid", false)
        val showLocation = cfg.getBoolean("discord.embed.show-location", true)
        val showTimestamp = cfg.getBoolean("discord.embed.show-timestamp", true)

        // Field names
        val fieldNamePlayer = cfg.getString("discord.embed.field-name-player", "Player").ifNullOrEmpty("Player")
        val fieldNameItemType = cfg.getString("discord.embed.field-name-item-type", "Item Type").ifNullOrEmpty("Item Type")
        val fieldNameUuid = cfg.getString("discord.embed.field-name-uuid", "Item UUID").ifNullOrEmpty("Item UUID")
        val fieldNameLocation = cfg.getString("discord.embed.field-name-location", "Location").ifNullOrEmpty("Location")

        // Build fields array
        val fields = mutableListOf<String>()
        if (showPlayer) {
            fields.add(buildField(fieldNamePlayer, "`${alert.playerName}`", true))
        }
        if (showItemType) {
            fields.add(buildField(fieldNameItemType, "`${alert.itemType}`", true))
        }
        if (showUuid) {
            val uuidDisplay = if (showFullUuid) "`${alert.itemUUID}`" else "`$shortUuid...`"
            fields.add(buildField(fieldNameUuid, uuidDisplay, false))
        }
        if (showLocation) {
            fields.add(buildField(fieldNameLocation, "`${alert.location}`", false))
        }

        // Footer
        val footerTextTemplate = cfg.getString("discord.embed.footer-text", "") ?: ""
        val footerText = applyPlaceholders(footerTextTemplate, placeholders)
        val footerIconUrl = cfg.getString("discord.embed.footer-icon-url", "") ?: ""

        // Images
        val thumbnailUrl = cfg.getString("discord.embed.thumbnail-url", "") ?: ""
        val imageUrl = cfg.getString("discord.embed.image-url", "") ?: ""

        // Mentions
        val mentionsEnabled = cfg.getBoolean("discord.mentions.enabled", false)
        var content = ""
        if (mentionsEnabled) {
            val roleId = cfg.getString("discord.mentions.role-id", "") ?: ""
            val userIdsStr = cfg.getString("discord.mentions.user-ids", "") ?: ""
            val contentTemplate = cfg.getString("discord.mentions.content", "{role_mention} {user_mentions}") ?: "{role_mention} {user_mentions}"

            val roleMention = if (roleId.isNotBlank()) "<@&$roleId>" else ""
            val userMentions = userIdsStr.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ") { "<@$it>" }

            val mentionPlaceholders = placeholders + mapOf(
                "{role_mention}" to roleMention,
                "{user_mentions}" to userMentions
            )
            content = applyPlaceholders(contentTemplate, mentionPlaceholders).trim()
        }

        // Build the JSON payload
        return buildJsonPayload(
            username = username,
            avatarUrl = avatarUrl,
            content = content,
            title = title,
            description = description,
            color = colorDecimal,
            fields = fields,
            footerText = footerText,
            footerIconUrl = footerIconUrl,
            thumbnailUrl = thumbnailUrl,
            imageUrl = imageUrl,
            showTimestamp = showTimestamp
        )
    }

    private fun buildField(name: String, value: String, inline: Boolean): String {
        return """{"name": ${escapeJson(name)}, "value": ${escapeJson(value)}, "inline": $inline}"""
    }

    private fun buildJsonPayload(
        username: String,
        avatarUrl: String,
        content: String,
        title: String,
        description: String,
        color: Int,
        fields: List<String>,
        footerText: String,
        footerIconUrl: String,
        thumbnailUrl: String,
        imageUrl: String,
        showTimestamp: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("{")

        // Username
        sb.append("\"username\": ${escapeJson(username)}")

        // Avatar URL
        if (avatarUrl.isNotBlank()) {
            sb.append(", \"avatar_url\": ${escapeJson(avatarUrl)}")
        }

        // Content (for mentions)
        if (content.isNotBlank()) {
            sb.append(", \"content\": ${escapeJson(content)}")
        }

        // Embed
        sb.append(", \"embeds\": [{")
        sb.append("\"title\": ${escapeJson(title)}")
        sb.append(", \"color\": $color")

        if (description.isNotBlank()) {
            sb.append(", \"description\": ${escapeJson(description)}")
        }

        // Fields
        if (fields.isNotEmpty()) {
            sb.append(", \"fields\": [${fields.joinToString(",")}]")
        }

        // Footer
        if (footerText.isNotBlank()) {
            sb.append(", \"footer\": {\"text\": ${escapeJson(footerText)}")
            if (footerIconUrl.isNotBlank()) {
                sb.append(", \"icon_url\": ${escapeJson(footerIconUrl)}")
            }
            sb.append("}")
        }

        // Thumbnail
        if (thumbnailUrl.isNotBlank()) {
            sb.append(", \"thumbnail\": {\"url\": ${escapeJson(thumbnailUrl)}}")
        }

        // Image
        if (imageUrl.isNotBlank()) {
            sb.append(", \"image\": {\"url\": ${escapeJson(imageUrl)}}")
        }

        // Timestamp
        if (showTimestamp) {
            sb.append(", \"timestamp\": \"${java.time.Instant.now()}\"")
        }

        sb.append("}]}")
        return sb.toString()
    }

    /**
     * Send a test message to verify webhook configuration.
     * Returns a result message indicating success or failure reason.
     */
    fun sendTestMessage(): String {
        val webhookUrl = plugin.config.getString("discord.webhook-url")

        if (!plugin.config.getBoolean("discord.enabled", false)) {
            return "Discord webhook is disabled in config"
        }

        if (webhookUrl.isNullOrBlank()) {
            return "Discord webhook URL is not configured"
        }

        return try {
            val cfg = plugin.config
            val username = cfg.getString("discord.username", "DupeTrace") ?: "DupeTrace"
            val avatarUrl = cfg.getString("discord.avatar-url", "") ?: ""
            val footerTextTemplate = cfg.getString("discord.embed.footer-text", "") ?: ""
            val footerText = applyPlaceholders(footerTextTemplate, mapOf("{version}" to plugin.pluginMeta.version))
            val footerIconUrl = cfg.getString("discord.embed.footer-icon-url", "") ?: ""

            val payload = buildJsonPayload(
                username = username,
                avatarUrl = avatarUrl,
                content = "",
                title = "✅ Webhook Test Successful",
                description = "DupeTrace is now connected to this Discord channel!\n\nYour customized webhook settings are working correctly.",
                color = 65280, // Green
                fields = listOf(
                    buildField("Plugin Version", "`${plugin.pluginMeta.version}`", true),
                    buildField("Server", "`${plugin.server.name}`", true)
                ),
                footerText = footerText,
                footerIconUrl = footerIconUrl,
                thumbnailUrl = cfg.getString("discord.embed.thumbnail-url", "") ?: "",
                imageUrl = "",
                showTimestamp = true
            )

            sendWebhook(webhookUrl, payload)
            "Test message sent successfully!"
        } catch (e: Exception) {
            "Failed to send test: ${e.message}"
        }
    }

    /**
     * Get current rate limit status.
     */
    fun getRateLimitStatus(): String {
        val maxPerMinute = plugin.config.getInt("discord.rate-limit.max-per-minute", 30)
        val queueSize = alertQueue.size
        val sent = sentThisMinute.get()
        return "Sent: $sent/$maxPerMinute this minute | Queued: $queueSize"
    }

    private fun sendWebhook(url: String, jsonPayload: String) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "DupeTrace-Webhook/${plugin.pluginMeta.version}")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.outputStream.use { os: OutputStream ->
                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                // Rate limited by Discord
                val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 60
                plugin.logger.warning("[Discord] Rate limited by Discord. Retry after ${retryAfter}s")
            } else if (responseCode !in 200..299) {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                plugin.logger.warning("[Discord] Webhook returned error code $responseCode: $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHexColor(hex: String): Int {
        val cleanHex = hex.removePrefix("#").trim()
        return try {
            cleanHex.toInt(16)
        } catch (e: NumberFormatException) {
            16711680 // Default red
        }
    }

    private fun applyPlaceholders(template: String, placeholders: Map<String, String>): String {
        var result = template
        placeholders.forEach { (key, value) ->
            result = result.replace(key, value)
        }
        return result
    }

    private fun escapeJson(str: String): String {
        val escaped = str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun String?.ifNullOrEmpty(default: String): String {
        return if (this.isNullOrBlank()) default else this
    }
}
