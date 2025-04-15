package com.projectX.casting

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

/**
 * Casting 클래스는 플레이어가 가구와 상호작용할 때(예: baseEntity 이름에 "Formwork" 포함)
 * 주조 인벤토리를 열어 플레이어가 직접 빵을 올려야 미니게임이 시작되도록 합니다.
 */
class Casting(private val plugin: Plugin) : Listener {

    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        val baseEntity = event.baseEntity
        if (baseEntity.name.contains("Formwork")) {
            openCastingInventory(event.player)
        }
    }

    // 플레이어가 인벤토리의 지정 슬롯에 빵을 올리면 CastingMinigame이 시작됩니다.
    private fun openCastingInventory(player: Player) {
        // 54칸 인벤토리 생성, 제목: "주조하기"
        val inventory: Inventory = Bukkit.createInventory(null, 54, Component.text("주조하기"))

        // 재료 슬롯(예: 40번)에 안내용 아이템(PAPER) 배치
        val ingredientSlot = 40
        inventory.setItem(ingredientSlot, createIngredientPlaceholderItem())

        // 미니게임 이벤트 슬롯(B 슬롯) 설정
        val bSlots = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
        for (slot in bSlots) {
            inventory.setItem(slot, createPlaceholderItem())
        }

        // 플레이어에게 인벤토리 열기
        player.openInventory(inventory)

        // 임시 리스너 등록: 플레이어가 지정 슬롯에 빵(BREAD)을 올리면 미니게임 시작
        val tempListener = object : Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                if (event.whoClicked != player) return
                if (event.inventory != inventory) return
                if (event.rawSlot != ingredientSlot) return

                val breadForMinigame = handleIngredientSlotClick(event, ingredientSlot, player, inventory)
                if (breadForMinigame != null) {
                    HandlerList.unregisterAll(this)
                    // 빵 아이템을 재료로 CastingMinigame 시작 (클릭 허용 시간: 3초)
                    val removalTime = 3.0
                    CastingMinigame(plugin, player, breadForMinigame, removalTime) { ingot, quality ->
                        player.sendMessage("주조가 완료되었습니다! 생성된 주괴 품질: $quality")
                        player.inventory.addItem(ingot)
                    }.start()
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(tempListener, plugin)
    }

    // 재료 슬롯 안내용 플레이스홀더 아이템 (PAPER)
    private fun createIngredientPlaceholderItem(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta
        meta?.displayName(Component.text("여기에 빵을 올려주세요", NamedTextColor.YELLOW))
        item.itemMeta = meta
        return item
    }

    // 기본 플레이스홀더 아이템 (흰색 유리판)
    private fun createPlaceholderItem(): ItemStack {
        val item = ItemStack(Material.WHITE_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }

    private fun handleIngredientSlotClick(
        event: InventoryClickEvent,
        ingredientSlot: Int,
        player: Player,
        inventory: Inventory
    ): ItemStack? {
        val currentItem = event.currentItem ?: return null
        if (currentItem.type != Material.BREAD) return null

        event.isCancelled = true
        val totalCount = currentItem.amount
        // CastingMinigame에 사용할 빵은 1개만 사용
        val breadForMinigame = currentItem.clone().apply { amount = 1 }
        // 남은 빵이 있다면 플레이어 인벤토리로 반환
        if (totalCount > 1) {
            inventory.setItem(ingredientSlot, null)
            val leftoverBread = currentItem.clone().apply { amount = totalCount - 1 }
            player.inventory.addItem(leftoverBread)
        } else {
            inventory.setItem(ingredientSlot, null)
        }
        return breadForMinigame
    }
}

/**
 * CastingMinigame 클래스는 주조 미니게임 로직을 담당합니다.
 * - A 슬롯(예: 41번): 플레이어가 올린 빵(ingredientItem)을 표시합니다.
 * - B 슬롯(9곳): 랜덤 이벤트(타버림 또는 거품)가 발생하며, 클릭에 따라 상태가 변경됩니다.
 * 미니게임 종료 후 성공 횟수에 따라 주괴 품질을 결정하고, 결과를 플레이어에게 전달합니다.
 */
class CastingMinigame(
    private val plugin: Plugin,
    private val player: Player,
    private val ingredientItem: ItemStack,
    private val removalTime: Double, // 클릭 허용 시간(초)
    private val onFinish: (ingot: ItemStack, quality: String) -> Unit
) : BukkitRunnable(), Listener {

    private val inventory: Inventory =
        Bukkit.createInventory(null, 54, Component.text("주조하기"))
    private val aSlot = 40
    private val bSlots = listOf(12, 13, 14, 21, 22, 23, 30, 31, 32)

    // 각 슬롯의 상태를 관리하는 데이터 클래스
    data class CastingSlot(
        val slot: Int,
        var state: SlotState = SlotState.NONE,
        var cycleCount: Int = 0, // FOAM 이벤트 재시도 횟수 (최대 4회)
        var timer: Int = 0,      // 틱 타이머 (20틱 = 1초)
        var startTick: Int = 0   // 이벤트 시작 시점(틱)
    )

    // 슬롯 상태 종류
    enum class SlotState {
        NONE,
        BURNT,
        FOAM,
        SUCCESS,
        FAIL
    }

    // 미니게임 이벤트 슬롯들을 관리할 맵
    private val castingSlots = mutableMapOf<Int, CastingSlot>()
    private var ticksElapsed = 0
    private val totalDurationTicks = 10 * 20  // 10초 동안 진행

    init {
        // A 슬롯에 플레이어가 올린 빵 재료 배치
        inventory.setItem(aSlot, ingredientItem)
        // B 슬롯 초기화: 각 슬롯에 무작위 시작 틱 할당 및 플레이스홀더 배치
        for (slot in bSlots) {
            val startTick = Random.nextInt(totalDurationTicks / 2)
            castingSlots[slot] = CastingSlot(slot, SlotState.NONE, 0, 0, startTick)
            inventory.setItem(slot, createPlaceholderItem())
        }
        // 플레이어에게 인벤토리 열기 및 이벤트 리스너 등록
        player.openInventory(inventory)
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    // 기본 플레이스홀더 아이템 (흰색 유리판)
    private fun createPlaceholderItem(): ItemStack {
        val item = ItemStack(Material.WHITE_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }

    // 타버림 상태 아이템 (CHARCOAL)
    private fun createBurntItem(): ItemStack {
        val item = ItemStack(Material.CHARCOAL)
        val meta = item.itemMeta
        meta?.displayName(Component.text("타버림", NamedTextColor.RED))
        item.itemMeta = meta
        return item
    }

    // 거품 상태 아이템 (GOLD_NUGGET)
    private fun createFoamItem(): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta
        meta?.displayName(Component.text("거품", NamedTextColor.YELLOW))
        item.itemMeta = meta
        return item
    }

    // 성공/실패 마크 아이템 (OAK_SIGN)
    private fun createMarkItem(mark: String): ItemStack {
        val item = ItemStack(Material.OAK_SIGN)
        val meta = item.itemMeta
        meta?.displayName(Component.text(mark, NamedTextColor.GREEN))
        item.itemMeta = meta
        return item
    }

    // 주괴 아이템 생성 (GOLDEN_APPLE) – 품질 표시 포함
    private fun createIngotItem(quality: String): ItemStack {
        val item = ItemStack(Material.GOLDEN_APPLE)
        val meta = item.itemMeta
        meta?.displayName(Component.text("주괴 [$quality]", NamedTextColor.GOLD))
        item.itemMeta = meta
        return item
    }

    override fun run() {
        ticksElapsed++
        // 각 이벤트 슬롯 상태 처리
        for ((slot, castingSlot) in castingSlots) {
            when (castingSlot.state) {
                SlotState.NONE -> {
                    if (ticksElapsed >= castingSlot.startTick) {
                        if (Random.nextBoolean()) {
                            castingSlot.state = SlotState.BURNT
                            inventory.setItem(slot, createBurntItem())
                        } else {
                            castingSlot.state = SlotState.FOAM
                            inventory.setItem(slot, createFoamItem())
                        }
                        castingSlot.timer = 0
                    }
                }
                SlotState.BURNT -> {
                    castingSlot.timer++
                    if (castingSlot.timer >= (removalTime * 20).toInt() && castingSlot.state == SlotState.BURNT) {
                        castingSlot.state = SlotState.FAIL
                        inventory.setItem(slot, createMarkItem("실패"))
                    }
                }
                SlotState.FOAM -> {
                    castingSlot.timer++
                    if (castingSlot.timer >= 20 && castingSlot.state == SlotState.FOAM) {
                        inventory.setItem(slot, createPlaceholderItem())
                        if (castingSlot.cycleCount < 4 && Random.nextBoolean()) {
                            castingSlot.state = SlotState.BURNT
                            castingSlot.timer = 0
                            castingSlot.cycleCount++
                            inventory.setItem(slot, createBurntItem())
                        } else {
                            castingSlot.state = SlotState.FAIL
                            inventory.setItem(slot, createMarkItem("실패"))
                        }
                    }
                }
                else -> {
                    // SUCCESS 또는 FAIL 상태는 그대로 유지
                }
            }
        }
        // 전체 미니게임 시간(10초) 종료 시 처리
        if (ticksElapsed >= totalDurationTicks) {
            cancel()
            finishCasting()
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked != player) return
        if (event.inventory != inventory) return
        val slot = event.rawSlot
        if (!castingSlots.containsKey(slot)) return
        val castingSlot = castingSlots[slot] ?: return

        when (castingSlot.state) {
            SlotState.BURNT -> {
                castingSlot.state = SlotState.SUCCESS
                inventory.setItem(slot, createMarkItem("성공"))
            }
            SlotState.FOAM -> {
                castingSlot.state = SlotState.FAIL
                inventory.setItem(slot, createMarkItem("실패"))
            }
            else -> {
                // SUCCESS, FAIL 상태면 변화 없음
            }
        }
        event.isCancelled = true
    }

    // 미니게임 종료 후 SUCCESS 개수를 집계하여 주괴 품질 결정 및 결과 반환
    private fun finishCasting() {
        val successCount = castingSlots.values.count { it.state == SlotState.SUCCESS }
        val quality = when (successCount) {
            9 -> "최고급"
            7, 8 -> "고급"
            in 4..6 -> "보통"
            2, 3 -> "하급"
            1 -> "최하급"
            else -> "미흡"
        }
        val ingot = createIngotItem(quality)
        player.sendMessage("주조 미니게임 종료: 성공 횟수 = $successCount, 주괴 품질 = $quality")
        player.closeInventory()
        onFinish(ingot, quality)
    }

    // 미니게임 시작 (1틱 간격 실행)
    fun start() {
        this.runTaskTimer(plugin, 0L, 1L)
    }
}
