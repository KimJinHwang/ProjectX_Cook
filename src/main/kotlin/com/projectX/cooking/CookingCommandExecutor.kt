package com.projectX.cooking

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CookingCommandExecutor(private val cooking: Cooking) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender is Player) {
            if (cooking.isCooking(sender)) {
                sender.sendMessage("이미 요리 중입니다.")
                return true
            }
            cooking.openCookingInventory(sender)
            return true
        }
        sender.sendMessage("This command can only be used by players.")
        return false
    }
}
