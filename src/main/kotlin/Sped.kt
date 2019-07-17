package org.othercraft

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player


class SpedCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player){
            if ( sender.isInsideVehicle ){
                val v = sender.vehicle!!
                if (v is Minecart){
                    v.maxSpeed = if (args.isEmpty()) 0.4 else args[0].toDouble()
                }
            }
        }
        return true
    }
}