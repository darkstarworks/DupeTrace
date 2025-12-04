package io.github.darkstarworks.dupeTrace.webhook

import org.bukkit.plugin.java.JavaPlugin
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Simple Discord webhook integration for sending duplicate alerts.
 *
 * Author: darkstarworks | https://github.com/darkstarworks
 * Donate: https://ko-fi/darkstarworks
 */
class DiscordWebhook(private val plugin: JavaPlugin) {

    private val webhookUrl: String? = plugin.config.getString("discord.webhook-url")
    private val enabled: Boolean = plugin.config.getBoolean("discord.enabled", false)

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
        if (!enabled || webhookUrl.isNullOrBlank()) return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val tagSuffix = if (tags.isNotEmpty()) " [${tags.joinToString(",")}]" else ""
                val shortId = itemUUID.take(8)

                val embed = """
                {
                  "username": "DupeTrace Alert",
                  "avatar_url": "https://i.imgur.com/AfFp7pu.png",
                  "embeds": [{
                    "title": "🚨 Duplicate Item Detected$tagSuffix",
                    "color": 16711680,
                    "fields": [
                      {
                        "name": "Player",
                        "value": "`$playerName`",
                        "inline": true
                      },
                      {
                        "name": "Item Type",
                        "value": "`$itemType`",
                        "inline": true
                      },
                      {
                        "name": "Item UUID",
                        "value": "`$shortId...`",
                        "inline": false
                      },
                      {
                        "name": "Location",
                        "value": "`$location`",
                        "inline": false
                      }
                    ],
                    "timestamp": "${java.time.Instant.now()}"
                  }]
                }
                """.trimIndent()

                sendWebhook(webhookUrl, embed)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to send Discord webhook: ${e.message}")
            }
        })
    }

    /**
     * Send a test message to verify webhook configuration.
     */
    fun sendTestMessage(): Boolean {
        if (!enabled || webhookUrl.isNullOrBlank()) {
            plugin.logger.warning("Discord webhook is not enabled or URL is missing")
            return false
        }

        return try {
            val embed = """
            {
              "username": "DupeTrace",
              "avatar_url": "https://i.imgur.com/AfFp7pu.png",
              "embeds": [{
                "title": "✅ Webhook Test Successful",
                "description": "DupeTrace is now connected to this Discord channel!",
                "color": 65280,
                "timestamp": "${java.time.Instant.now()}"
              }]
            }
            """.trimIndent()

            sendWebhook(webhookUrl, embed)
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to send test webhook: ${e.message}")
            false
        }
    }

    private fun sendWebhook(url: String, jsonPayload: String) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "DupeTrace-Webhook")
            connection.doOutput = true

            connection.outputStream.use { os: OutputStream ->
                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                plugin.logger.warning("Discord webhook returned error code $responseCode: $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
}
