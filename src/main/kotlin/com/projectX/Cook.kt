package com.projectX

import com.projectX.casting.Casting
import com.projectX.cooking.Cooking
import com.projectX.cooking.CookingCommandExecutor
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin

class Cook : JavaPlugin() {

    override fun onEnable() {
        server.pluginManager.registerEvents(Cooking(this), this)
        server.pluginManager.registerEvents(Casting(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
