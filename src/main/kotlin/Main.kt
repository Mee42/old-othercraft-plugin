package org.othercraft

import discord4j.core.`object`.util.Snowflake
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.http.client.ClientException
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.awt.Color
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt


class OthercraftPlugin : JavaPlugin() {

    lateinit var discord: Discord

    override fun onEnable() {
        println("## onEnable")
        discord = Discord()
        getCommand("oc-status")?.setExecutor(StatusCommand()) ?: logger.severe("Error adding commands")
        getCommand("backup")?.setExecutor(BackupCommand(this)) ?: logger.severe("Error adding commands")


        server.pluginManager.registerEvents(MyListener(this), this)
        discord.login()
        val timer = Timer()
        val hourlyTask = object : TimerTask() {
            override fun run() {
                backup()
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
            } else {
                spec.setTimestamp(start)
                    .setFooter("Started at ", null)
            }



            val s = StringBuffer()
            for (p in this.server.onlinePlayers) {
                if (p.gameMode == GameMode.SPECTATOR)
                    continue
                s.append("**")
                s.append(p.playerListName)
                s.append("**")
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

    private fun updateBoard(spec: (EmbedCreateSpec) -> Any) {
        discord.statusChannel
            .flatMap { channel -> channel.getMessagesBefore(Snowflake.of(Instant.now())).next() }
            .flatMap { it.edit { w -> w.setEmbed { u -> spec(u) } } }
//            .switchIfEmpty {
//                discord.statusChannel.flatMap { channel -> channel.createEmbed { u -> spec(u) } }
//            }
            .subscribe()
    }

}


infix fun CommandSender.send(text: String) {
    spigot().sendMessage(TextComponent(text))
}


class MyListener(private val plugin: OthercraftPlugin) : Listener {

    private val d = plugin.discord

    @EventHandler
    fun f(event: PlayerJoinEvent) {
        event.joinMessage = "§6Welcome to Othercraft, " + event.player.name + "!"
        d.log(event.player.name + " logged in")
        plugin.updateBoard()
    }

    @EventHandler
    fun f(event: PlayerKickEvent) {
        d.log(event.player.name + " was kicked. Reason:" + event.reason)
    }

    @EventHandler
    fun f(event: PlayerQuitEvent) {
        d.log(event.player.name + " left")
        plugin.updateBoard()
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

}