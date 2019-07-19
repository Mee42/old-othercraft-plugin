package org.othercraft

import discord4j.core.`object`.util.Snowflake
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
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.thread


class OthercraftPlugin : JavaPlugin() {

    lateinit var discord: Discord

    override fun onEnable() {
        println("## onEnable")
        discord = Discord()
        getCommand("oc-status")?.setExecutor(StatusCommand()) ?: logger.severe("Error adding commands")
        getCommand("backup")?.setExecutor(BackupCommand(this)) ?: logger.severe("Error adding commands")
        getCommand("sped")?.setExecutor(SpedCommand()) ?: logger.severe("Error adding commands")



        server.pluginManager.registerEvents(MyListener(this), this)
        discord.login()
        val timer = Timer()
        val hourlyTask = object : TimerTask() {
            override fun run() {
                if (LocalDateTime.now().hour % 6 == 0 ){
                    backup()
                } else {
                    copy()
                }
            }
        }

        val c = Calendar.getInstance()
        c.add(Calendar.HOUR_OF_DAY, 1)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val msTillHour = c.timeInMillis - System.currentTimeMillis()
        logger.info("MS till hour: $msTillHour")
        timer.schedule(hourlyTask, msTillHour, 1000 * 60 * 60)

        Flux.interval(Duration.ofMinutes(1))
            .subscribe { updateBoard() }

        discord.log("Server Started")
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


    @Volatile
    private var backingUp = false


    fun copy() {
        if (backingUp)
            return
        backingUp = true
        thread {
            discord.log("Copying")
            val pb = ProcessBuilder("copy.sh")
            pb.inheritIO()
            pb.directory(File("../"))
            pb.start().waitFor()
            backingUp = false
        }
    }

    fun backup(): Boolean {
        if (backingUp)
            return false
        backingUp = true
        thread {
            this.server.spigot().broadcast(TextComponent("Backing up..."))
            discord.log("Backing up")
            val backups = File("../backups")
                .listFiles()
                ?.size ?: -1
            val pb = ProcessBuilder("backup.sh")
            pb.inheritIO()
            pb.directory(File("../"))
            pb.start().waitFor()
            val new = File("../backups")
                .listFiles()
                ?.size ?: -1
            discord.log("Backed up. Initial: $backups now: $new delta: ${new - backups}")
            this.server.spigot().broadcast(TextComponent("Done!"))
            lastBackedUp = Instant.now()
            updateBoard()
            backingUp = false
        }
        return true
    }

    private var lastBackedUp: Instant? = null
    private val start: Instant = Instant.now()


    // info to publish:
    //  online players (not including spectator)
    //  uptime
    fun updateBoard() {
        val spec = { spec: EmbedCreateSpec ->
            spec.setTitle("Othercraft")
                .setUrl("https://othercraft.org")


            if (lastBackedUp != null) {
                spec.setTimestamp(lastBackedUp ?: error("lastBackedUp set to null after being non-null"))
                    .setFooter("Last backup was at ", null)
                spec.addField("Uptime",Duration.between(Instant.now(),start).toString(),false)
            } else {
                spec.setTimestamp(start)
                    .setFooter("Started at ", null)
            }

            spec.setDescription(
                "**Weather**:" + this.server.getWorld("world")?.weather() + "\n" +
                        if (isDay("world")) "Day \uD83C\uDF1E🌞🌞" else "Night \uD83C\uDF19"
            )
        spec.addField("Weather",this.server.getWorld("world")?.weather() ?: "unknown",false)

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