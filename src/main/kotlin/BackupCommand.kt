package org.othercraft

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class BackupCommand(private val plugin: OthercraftPlugin) : CommandExecutor  {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        sender send "can't back up :("
        return true
    }

}