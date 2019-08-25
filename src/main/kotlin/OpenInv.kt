package org.othercraft

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class OpenInvCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.isOp)
            return false
        val userStr = args.first()
        val user =  sender.server.getPlayer(userStr) ?: return false
        user.inventory
        if (sender !is Player)
            return false
        sender.openInventory(user.inventory)
        return true
    }

}
