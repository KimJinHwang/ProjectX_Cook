package com.projectX.cooking

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

class CookingCompletenessGauge(
    private val player: Player,
    private val resultItem: ItemStack,
    private val cookingPlayers: MutableSet<Player>
) : BukkitRunnable(), Listener {

    private val barLength = 30
    private var cursorPosition = 0
    private var stopped = false
    private var stopTickCounter = 0
    private var keepShowingGauge = false
    private var tickCount = 0

    override fun run() {
        tickCount++ // 항상 증가

        if (stopped) {
            if (keepShowingGauge && stopTickCounter < 40) {
                player.sendActionBar(buildGaugeBar(cursorPosition)) // 계속 깜빡임 유지됨
                stopTickCounter++
                return
            }
            player.sendMessage("요리가 끝났습니다!")
            cookingPlayers.remove(player)
            this.cancel()
            return
        }

        if (cursorPosition >= barLength) {
            stopCooking(cursorPosition)
            return
        }

        player.sendActionBar(buildGaugeBar(cursorPosition))
        cursorPosition++
    }

    private fun buildGaugeBar(cursor: Int): Component {
        val builder = Component.text()
        val blinkColor = when (tickCount % 4) {
            0 -> NamedTextColor.WHITE
            1 -> NamedTextColor.AQUA
            2 -> NamedTextColor.YELLOW
            else -> NamedTextColor.WHITE
        }

        for (i in 0 until barLength) {
            val baseColor = when (i) {
                in 0..11 -> NamedTextColor.RED
                in 12..20 -> NamedTextColor.GOLD
                in 21..25 -> NamedTextColor.YELLOW
                in 26..27 -> NamedTextColor.GREEN
                in 28..29 -> NamedTextColor.DARK_GREEN
                else -> NamedTextColor.GRAY
            }

            val color = if (i == cursor) blinkColor else baseColor
            builder.append(Component.text("▌", color))
        }

        return builder.build()
    }

    private fun stopCooking(position: Int) {
        stopped = true
        keepShowingGauge = true
        val score = when (position) {
            in 0..11 -> 30
            in 12..20 -> 50
            in 21..25 -> 70
            in 26..28 -> 85
            29 -> 100
            else -> 0
        }

        val result = resultItem.clone()
        val meta = result.itemMeta
        meta?.let {
            it.displayName(Component.text("완성도 $score%", NamedTextColor.GOLD))
            result.itemMeta = it
        }

        // 시각 피드백 (Title)
        val titleMain = Component.text("🍽 완성!", NamedTextColor.GREEN)
        val titleSub = Component.text("완성도 $score%", NamedTextColor.YELLOW)

        val title = Title.title(
            titleMain,
            titleSub,
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        )

        player.showTitle(title)

        player.sendMessage("완성도 $score% 요리가 완성되었습니다!")
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.inventory.addItem(result)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.player == player && !stopped && event.action.isRightClick) {
            stopCooking(cursorPosition)
        }
    }
}
