package org.othercraft

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.Calendar
import java.io.File
import kotlin.concurrent.thread


class OthercraftPlugin : JavaPlugin() {

    lateinit var discord: Discord

    override fun onEnable() {
        println("## onEnable")
        discord = Discord()
        getCommand("oc-status")?.setExecutor(StatusCommand()) ?: logger.severe("Error adding commands")
        getCommand("backup")?.setExecutor(BackupCommand(this)) ?: logger.severe("Error adding commands")



        server.pluginManager.registerEvents(MyListener(discord), this)
        discord.login()
        discord.log("Server Started")
        val timer = Timer()
        val hourlyTask = object : TimerTask() { override fun run() { backup() } }

        val c = Calendar.getInstance()
        c.add(Calendar.HOUR_OF_DAY, 1)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val msTillHour = c.timeInMillis - System.currentTimeMillis()
        logger.info("MS till hour: $msTillHour")
        timer.schedule(hourlyTask, msTillHour, 1000 * 60 * 60)
    }

    override fun onDisable() {
        println("## D4J logging out")
        discord.logChannel
            .flatMap { c -> c.createMessage("Server stopped") }
            .then(discord.client.logout())
            .block()
        println("## D4J logging out DONE")
    }


    fun backup(){
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
        }
    }
}


fun updateBoard(){
    TODO()
}


infix fun CommandSender.send(text: String) {
    spigot().sendMessage(TextComponent(text))
}



class MyListener(private val discord: Discord) : Listener {

    @EventHandler
    fun f(event: PlayerJoinEvent) {
        event.joinMessage = "Welcome to Othercraft, " + event.player.name + "!"
        discord.log(event.player.name + " logged in")
    }

    @EventHandler
    fun f(event: PlayerKickEvent) {
        discord.log(event.player.name + " was kicked. Reason:" + event.reason)
    }

    @EventHandler
    fun f(event: PlayerQuitEvent) {
        discord.log(event.player.name + " left")
    }

    @EventHandler
    fun f(event: PlayerDeathEvent) {
        discord.log(event.deathMessage ?: "hmm")
    }

}