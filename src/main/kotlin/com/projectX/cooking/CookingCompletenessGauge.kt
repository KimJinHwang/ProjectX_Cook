package com.projectX.cooking

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

class CookingCompletenessGauge(
    val player: Player,
    private val resultItem: ItemStack,
    private val location: Location,
    private val onFinish: () -> Unit
) : BukkitRunnable(), Listener {

    private val barLength = 30
    private var cursorPosition = 0
    private var stopped = false
    private var stopTickCounter = 0
    private var keepShowingGauge = false
    private var tickCount = 0

    private lateinit var gaugeStand: ArmorStand

    private fun spawnGaugeStand() {
        gaugeStand = location.world.spawn(location.clone().add(0.0, 0.3, 0.0),
            ArmorStand::class.java).apply {
            isVisible = false
            setGravity(false)
            isMarker = false
            isCustomNameVisible = true
        }
    }

    private fun updateGaugeStand(cursor: Int) {
        gaugeStand.customName(buildGaugeBar(cursor))
    }

    override fun run() {
        tickCount++ // 항상 증가
        if (!::gaugeStand.isInitialized) {
            spawnGaugeStand()
        }

        if (stopped) {
            if (keepShowingGauge && stopTickCounter < 40) {
                updateGaugeStand(cursorPosition)
                stopTickCounter++
                return
            }
            player.sendMessage("요리가 끝났습니다!")
            gaugeStand.remove()
            onFinish()
            this.cancel()
            return
        }

        if (cursorPosition >= barLength) {
            stopCooking(cursorPosition)
            return
        }

        updateGaugeStand(cursorPosition)
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
        gaugeStand.remove()
        onFinish()
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.player == player && !stopped && event.action.isRightClick) {
            stopCooking(cursorPosition)
        }
    }
}
