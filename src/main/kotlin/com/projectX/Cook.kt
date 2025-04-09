package com.projectX

import com.projectX.cooking.Cooking
import com.projectX.cooking.CookingCommandExecutor
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin

class Cook : JavaPlugin() {

    override fun onEnable() {
        val cooking = Cooking(this)
        server.pluginManager.registerEvents(cooking, this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
