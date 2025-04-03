package com.projectX.cooking

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

class CookingProgress(
    private val player: Player,
    private val totalTime: Int,
    private val resultItem: ItemStack,
    private val cookingPlayer: MutableSet<Player>
) : BukkitRunnable() {
    private var elapsedTime = 0

    override fun run() {
        if (elapsedTime >= totalTime) {
            player.sendMessage("요리가 완료되었습니다!")
            player.inventory.addItem(resultItem)
            cookingPlayer.remove(player)
            cancel()
            return
        }

        val progress = elapsedTime.toDouble() / totalTime.toDouble()
        val progressBar = buildProgressBar(progress)
        player.sendActionBar(progressBar)
        elapsedTime++
    }

    private fun buildProgressBar(progress: Double): Component {
        val totalBars = 20
        val progressBars = (totalBars * progress).toInt()
        val remainingBars = totalBars - progressBars

        val progressBar = Component.text()
            .append(Component.text("|".repeat(progressBars), NamedTextColor.GREEN))
            .append(Component.text("|".repeat(remainingBars), NamedTextColor.RED))
            .build()

        return progressBar
    }
}