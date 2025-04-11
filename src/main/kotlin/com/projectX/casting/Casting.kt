package com.projectX.casting

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

/**
 * Casting 클래스는 플레이어가 가구와 상호작용할 때 (예: baseEntity의 이름에 "Formwork"가 포함된 경우)
 * 주조 미니게임 UI를 열어 주조 과정을 진행시킵니다.
 *
 * 미니게임은 내부에서 10초 동안 진행되며,
 * 지정된 슬롯(총 9곳)에서 랜덤으로 이벤트(타버림 또는 거품)가 발생합니다.
 * 플레이어가 해당 슬롯을 클릭하여 상태를 변경하면 성공(SUCCESS) 또는 실패(FAIL)로 처리되고,
 * 최종 성공 횟수에 따라 주조 품질이 결정되어 플레이어에게 결과(주괴 아이템)를 제공합니다.
 */
class Casting(private val plugin: Plugin) : Listener {

    // baseEntity의 이름에 "Formwork"가 포함된 경우 주조 UI를 엽니다.
    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        val baseEntity = event.baseEntity
        if (baseEntity.name.contains("Formwork")) {
            openCastingInventory(event.player, baseEntity.location)
        }
    }

    // 주조 UI(미니게임)를 열고 CastingMinigame 인스턴스를 시작합니다.
    fun openCastingInventory(player: Player, location: Location) {
        // 예시 재료: 빵(BREAD)을 사용 (원하는 재료로 변경 가능)
        val ingredientItem = ItemStack(Material.BREAD)
        // 클릭 허용 시간 (초)
        val removalTime = 1.0
        // 미니게임 시작 – 주조 완료 시 onFinish 콜백을 통해 결과 처리
        CastingMinigame(plugin, player, ingredientItem, location, removalTime) { ingot, quality ->
            player.sendMessage("주조가 완료되었습니다! 생성된 주괴 품질: $quality")
            // 예시: 생성된 주괴 아이템을 플레이어 인벤토리에 추가
            player.inventory.addItem(ingot)
        }.start()
    }
}

/**
 * CastingMinigame 클래스는 기존 Cooking 미니게임 로직을 주조 테마로 변경한 것입니다.
 * - 인벤토리 제목: "주조하기"
 * - A 슬롯(44번 슬롯): 주조 재료(ingredientItem) 표시
 * - B 슬롯(13, 14, 15, 23, 24, 25, 33, 34, 35번 슬롯): 미니게임 이벤트 영역
 *   각 슬롯에서 랜덤으로 "타버림" 또는 "거품" 이벤트가 발생하며,
 *   플레이어의 클릭에 따라 상태가 SUCCESS(성공) 또는 FAIL(실패)로 변경됩니다.
 * - 미니게임 종료 후 성공 횟수에 따라 주조 품질을 결정하고,
 *   주괴 아이템(예: GOLDEN_APPLE)을 생성하여 onFinish 콜백을 호출합니다.
 */
class CastingMinigame(
    private val plugin: Plugin,
    private val player: Player,
    private val ingredientItem: ItemStack,
    private val location: Location,
    private val removalTime: Double, // 클릭 허용 시간(초)
    private val onFinish: (ingot: ItemStack, quality: String) -> Unit
) : BukkitRunnable(), Listener {

    // 54칸 인벤토리, 제목 "주조하기"
    private val inventory: Inventory = Bukkit.createInventory(null, 54, Component.text("주조하기"))
    // A 슬롯: 주조 재료를 표시 (예: 슬롯 44)
    private val aSlot = 44
    // B 슬롯: 미니게임 이벤트를 진행할 9개 슬롯
    private val bSlots = listOf(13, 14, 15, 23, 24, 25, 33, 34, 35)

    // 각 슬롯의 상태를 관리하는 데이터 클래스
    data class CastingSlot(
        val slot: Int,
        var state: SlotState = SlotState.NONE,
        var cycleCount: Int = 0, // FOAM(거품) 이벤트 반복 횟수 (최대 4회)
        var timer: Int = 0,      // 틱 단위 타이머 (20틱 = 1초)
        var startTick: Int = 0   // 이벤트 시작 시점(틱)
    )

    // 슬롯 상태: NONE, BURNT(타버림), FOAM(거품), SUCCESS(성공), FAIL(실패)
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
        // A 슬롯에 주조 재료를 배치
        inventory.setItem(aSlot, ingredientItem)
        // B 슬롯 초기화: 각 슬롯마다 무작위 이벤트 시작 틱을 지정하고 플레이스홀더 설정
        for (slot in bSlots) {
            val startTick = Random.nextInt(totalDurationTicks / 2) // 전반부에 이벤트 발생
            castingSlots[slot] = CastingSlot(slot, SlotState.NONE, 0, 0, startTick)
            inventory.setItem(slot, createPlaceholderItem())
        }
        // 플레이어에게 인벤토리 열기
        player.openInventory(inventory)
        // 미니게임 관련 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    // 플레이스홀더 아이템: 흰색 유리판
    private fun createPlaceholderItem(): ItemStack {
        val item = ItemStack(Material.WHITE_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }

    // BURNT 상태 아이템: 타버림 표시 (CHARCOAL)
    private fun createBurntItem(): ItemStack {
        val item = ItemStack(Material.CHARCOAL)
        val meta = item.itemMeta
        meta?.displayName(Component.text("타버림", NamedTextColor.RED))
        item.itemMeta = meta
        return item
    }

    // FOAM 상태 아이템: 거품 표시 (GOLD_NUGGET)
    private fun createFoamItem(): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta
        meta?.displayName(Component.text("거품", NamedTextColor.YELLOW))
        item.itemMeta = meta
        return item
    }

    // 성공(SUCCESS) 또는 실패(FAIL) 표식을 생성하는 아이템
    private fun createMarkItem(mark: String): ItemStack {
        val item = ItemStack(Material.OAK_SIGN)
        val meta = item.itemMeta
        meta?.displayName(Component.text(mark, NamedTextColor.GREEN))
        item.itemMeta = meta
        return item
    }

    // 최종적으로 생성할 주괴 아이템(예: GOLDEN_APPLE)과 품질 표시
    private fun createIngotItem(quality: String): ItemStack {
        val item = ItemStack(Material.GOLDEN_APPLE)
        val meta = item.itemMeta
        meta?.displayName(Component.text("주괴 [$quality]", NamedTextColor.GOLD))
        item.itemMeta = meta
        return item
    }

    override fun run() {
        ticksElapsed++
        // 각 이벤트 슬롯의 상태 처리
        for ((slot, castingSlot) in castingSlots) {
            when (castingSlot.state) {
                SlotState.NONE -> {
                    // 지정된 시작 틱에 도달하면 이벤트 발생
                    if (ticksElapsed >= castingSlot.startTick) {
                        // BURNT(타버림) 또는 FOAM(거품) 이벤트를 50% 확률로 결정
                        if (Random.nextBoolean()) {
                            castingSlot.state = SlotState.BURNT
                            inventory.setItem(slot, createBurntItem())
                        } else {
                            castingSlot.state = SlotState.FOAM
                            inventory.setItem(slot, createFoamItem())
                        }
                        // 타이머 초기화
                        castingSlot.timer = 0
                    }
                }
                SlotState.BURNT -> {
                    // BURNT 상태: removalTime 이내에 클릭되지 않으면 FAIL 처리
                    castingSlot.timer++
                    if (castingSlot.timer >= (removalTime * 20).toInt() && castingSlot.state == SlotState.BURNT) {
                        castingSlot.state = SlotState.FAIL
                        inventory.setItem(slot, createMarkItem("실패"))
                    }
                }
                SlotState.FOAM -> {
                    // FOAM 상태: 1초(20틱) 이후 처리
                    castingSlot.timer++
                    if (castingSlot.timer >= 20 && castingSlot.state == SlotState.FOAM) {
                        // FOAM 상태 아이템 제거 후, 재시도(최대 4회)로 BURNT 상태 전환하거나 FAIL 처리
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
        // 전체 미니게임 시간(10초) 경과 시 미니게임 종료
        if (ticksElapsed >= totalDurationTicks) {
            cancel()
            finishCasting()
        }
    }

    // 미니게임 인벤토리 내 클릭 이벤트 처리
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked != player) return
        if (event.inventory != inventory) return
        val slot = event.rawSlot
        if (!castingSlots.containsKey(slot)) return
        val castingSlot = castingSlots[slot] ?: return

        when (castingSlot.state) {
            SlotState.BURNT -> {
                // BURNT 상태 클릭 시 SUCCESS 처리
                castingSlot.state = SlotState.SUCCESS
                inventory.setItem(slot, createMarkItem("성공"))
            }
            SlotState.FOAM -> {
                // FOAM 상태 클릭 시 FAIL 처리
                castingSlot.state = SlotState.FAIL
                inventory.setItem(slot, createMarkItem("실패"))
            }
            else -> {
                // SUCCESS, FAIL 상태 클릭 시 변화 없음
            }
        }
        event.isCancelled = true
    }

    // 미니게임 종료 후 각 슬롯의 SUCCESS 개수를 집계하여 주조 품질을 결정합니다.
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

    // 미니게임 시작: BukkitRunnable을 이용해 1틱 간격으로 run() 호출
    fun start() {
        this.runTaskTimer(plugin, 0L, 1L)
    }
}
