package org.othercraft

import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.plugin.java.JavaPlugin
import reactor.core.publisher.Flux
import reactor.core.publisher.switchIfEmpty
import java.awt.Color
import java.time.Duration
import java.time.Instant


enum class Joke(
    val matches: List<String>,
    val emoji: ReactionEmoji,
    val conditional: (MessageCreateEvent) -> Boolean = { true }){
    EGG(listOf("egg"),ReactionEmoji.unicode("\uD83E\uDD5A")),

    OC(listOf("oc","othercraft"),ReactionEmoji.custom(Snowflake.of("626209658981449731"),"oc",false),{
        it.message.channelId == Snowflake.of("513794575043657728")
    });



}

class OthercraftPlugin : JavaPlugin() {

    lateinit var discord: Discord

    override fun onEnable() {
        println("## onEnable")
        discord = Discord()
        getCommand("oc-status")?.setExecutor(StatusCommand()) ?: logger.severe("Error adding commands")
        getCommand("backup")?.setExecutor(BackupCommand(this)) ?: logger.severe("Error adding commands")
        getCommand("sped")?.setExecutor(SpedCommand()) ?: logger.severe("Error adding commands")

        getCommand("openinv")?.setExecutor(OpenInvCommand()) ?: logger.severe("Error adding commands")

        server.pluginManager.registerEvents(MyListener(this), this)
        discord.login()

        Flux.interval(Duration.ofMinutes(1))
            .subscribe { updateBoard() }
        discord.log("Server Started")

        discord.client.eventDispatcher.on(MessageCreateEvent::class.java)
            .concatMapIterable { Joke.values().map { w -> w to it } }
            .filter {
                it.second.message.content.map { content ->
                it.first.matches.any { str ->  content.contains(str,ignoreCase = true)}
            }.orElse(false) }
            .flatMap { it.second.message.addReaction(it.first.emoji) }
            .onErrorContinue { _, _ ->  }
            .subscribe()

        updateBoard()
    }

    override fun onDisable() {
        println("## D4J logging out")
        discord.logChannel
            .flatMap { c ->
                val spec = { spec: EmbedCreateSpec ->
                    spec.setTitle("Othercraft")
                        .setUrl("https://othercraft.org")

                    spec.setColor(Color.RED)

                    spec.setDescription("Offline")

                    spec.setTimestamp(Instant.now())
                        .setFooter("Last Online: ", null)

                }
                updateBoard(spec)
                c.createMessage("Server stopped")
            }
            .then(discord.client.logout())
            .subscribe()
        Thread.sleep(1000)
        println("## D4J logging out DONE")
    }



    private val start: Instant = Instant.now()


    // info to publish:
    //  online players (not including spectator)
    //  uptime
    fun updateBoard() {
        val spec = { spec: EmbedCreateSpec ->
            spec.setTitle("Othercraft")
                .setUrl("https://othercraft.org")


            spec.addField("Uptime",Duration.between(Instant.now(),start).toString(),false)

            spec.setTimestamp(start)
                .setFooter("Started at ", null)

            spec.setDescription(
                "**Weather**:" + this.server.getWorld("world")?.weather() + "\n" +
                        if (isDay("world")) "Day \uD83C\uDF1E🌞🌞" else "Night \uD83C\uDF19"
            )


            val s = StringBuffer()
            for (p in this.server.onlinePlayers) {
                if (p.gameMode == GameMode.SPECTATOR)
                    continue
                s.append("**")
                s.append(p.playerListName)
                s.append("**")
                s.append('\n')
            }


            if (s.trim().isNotEmpty()) {
                spec.addField("Players online", s.toString(), false)
                spec.setColor(Color.GREEN)
            } else {
                spec.setColor(Color.YELLOW)
            }

        }

        updateBoard(spec)
    }

    private fun isDay(worldname: String): Boolean {
        val time = server.getWorld(worldname)!!.time
        return time < 12300 || time > 23850

    }

    private fun updateBoard(spec: (EmbedCreateSpec) -> Any) {
        discord.statusChannel
            .flatMap { channel ->
                channel
                    .getMessagesBefore(Snowflake.of(Instant.now()))
                    .next()
            }
            .flatMap { it.edit { w -> w.setEmbed { u -> spec(u) } } }
            .switchIfEmpty {
                discord.statusChannel.flatMap { channel -> channel.createEmbed { u -> spec(u) } }
            }
            .onErrorMap { it.printStackTrace();it }
            .subscribe()
    }

}

private fun World.weather(): String {
    return when {
        this.hasStorm() && this.isThundering -> "Thundering"
        this.hasStorm() -> "Raining"
        else -> "Sunny"
    }
}


infix fun CommandSender.send(text: String) {
    spigot().sendMessage(TextComponent(text))
}

const val TIME = "TIME"

class MyListener(private val plugin: OthercraftPlugin) : Listener {

    private val d = plugin.discord

    private val map = mutableMapOf<String,Instant>()



    @EventHandler
    fun f(event: PlayerJoinEvent) {
        event.joinMessage = "§6Welcome to Othercraft, " + event.player.name + "!"
        d.log(event.player.name + " logged in")
        plugin.updateBoard()
        map[event.player.uniqueId.toString()] = Instant.now()
    }

    @EventHandler
    fun f(event: PlayerKickEvent) {
        d.log(event.player.name + " was kicked. Reason:" + event.reason)
    }

    @EventHandler
    fun f(event: PlayerQuitEvent) {
        d.log(event.player.name + " left")
        plugin.updateBoard()
        val duration = Duration.between(map[event.player.uniqueId.toString()] ?: Instant.now(),Instant.now())
        map.remove(event.player.uniqueId.toString())
        d.log(TIME + ":" + event.player.uniqueId.toString() + ":" + duration.toMillis())
    }

    @EventHandler
    fun f(event: PlayerDeathEvent) {
        d.log(event.deathMessage ?: "hmm")
    }


    @EventHandler
    fun f(event: PlayerGameModeChangeEvent) {
        plugin.updateBoard()
        event.player
    }

    @EventHandler
    fun f(event: WeatherChangeEvent) {
        plugin.updateBoard()
        event.toWeatherState()
    }





}