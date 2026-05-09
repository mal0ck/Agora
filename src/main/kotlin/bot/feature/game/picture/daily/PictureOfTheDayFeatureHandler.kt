package de.c4vxl.bot.feature.game.picture.daily

import de.c4vxl.bot.feature.game.picture.PictureFeature
import de.c4vxl.config.enums.Color
import de.c4vxl.utils.EmbedUtils.color
import net.dv8tion.jda.api.EmbedBuilder
import java.util.concurrent.ScheduledFuture

/**
 * A feature sending a random "picture of the day" every day at a fixed time into a selected channel
 */
class PictureOfTheDayFeatureHandler(val feature: PictureOfTheDayFeature) {
    /**
     * Returns the picture feature
     */
    private val pictureFeature: PictureFeature?
        get() = feature.bot.getFeature<PictureFeature>() ?: run {
            feature.logger.warn("Picture of the day doesn't work if 'PictureFeature' is not enabled! (Guild: ${feature.bot.guild.id})")
            null
        }

    private var task: ScheduledFuture<*>? = null

    /**
     * Sends the picture of the day
     */
    fun send(): Boolean {
        feature.logger.info("Sending picture of the day for '${feature.bot.guild.id}'")

        val categories: List<List<String>> = feature.bot.dataHandler.get<String>(feature.name, "category")
            ?.lowercase()
            ?.split("|")
            ?.map {
                it.replace(";", ",")
                    .replace(", ", ",")
                    .split(",")
            } ?: listOf()

        val category = categories.randomOrNull() ?: return false

        val channel =
            feature.bot.dataHandler.get<String>(feature.name, "channel")?.let {
                feature.bot.guild.getTextChannelById(it)
            }

        if (channel == null) {
            feature.logger.warn("Tried to send pic of the day, but no channel is set! (${feature.bot.guild.id})")
            return false
        }

        val first = category.firstOrNull() ?: return false
        val queries = category.drop(1).toTypedArray()
        val response = when (first.lowercase()) {
            "cat"  -> pictureFeature?.handler?.publicAPIs?.cat(*queries)
            "dog"  -> pictureFeature?.handler?.publicAPIs?.dog()
            "none" -> pictureFeature?.handler?.unsplashAPI?.random()
            else   -> pictureFeature?.handler?.unsplashAPI?.random(first, *queries)
        } ?: return false

        // Send
        channel.sendMessageEmbeds(
            EmbedBuilder()
            .setTitle(feature.bot.language.translate("feature.picture_of_the_day.embed.title"))
            .setDescription(feature.bot.language.translate("feature.picture_of_the_day.embed.desc", first))
            .setImage("attachment://${response.file?.name}")
            .color(Color.PRIMARY)
            .apply { response.creditsString?.let { this.setFooter(it) } }
            .build())
            .apply {
                response.file?.let { addFiles(it) }
            }
            .queue()

        return true
    }

    /**
     * Starts the daily picture schedule with the times in the config
     */
    fun reloadSchedule() {
        // Cancel task
        feature.tasks.cancel(task)

        // Return if not enabled
        if (feature.bot.dataHandler.get<String>(feature.name, "category") == null)
            return

        task = feature.tasks.scheduleDaily(
            feature.bot.dataHandler.get<Int>(feature.name, "h") ?: 12,
            feature.bot.dataHandler.get<Int>(feature.name, "m") ?: 0
        ) {
            send()
        }

        feature.logger.info("Starting PicOfTheDay task for '${feature.bot.guild.id}'")
    }
}