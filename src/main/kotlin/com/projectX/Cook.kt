package com.projectX

import com.projectX.cooking.Cooking
import com.projectX.cooking.CookingCommandExecutor
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin

class Cook : JavaPlugin() {

    override fun onEnable() {
        val cooking = Cooking(this)
        server.pluginManager.registerEvents(cooking, this)

        val openCookingInterfaceCommand: PluginCommand? = getCommand("open_cooking_interface")
        if (openCookingInterfaceCommand != null) {
            openCookingInterfaceCommand.setExecutor(CookingCommandExecutor(cooking))
        } else {
            logger.warning("Command 'open_cooking_interface' not found in plugin.yml")
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
