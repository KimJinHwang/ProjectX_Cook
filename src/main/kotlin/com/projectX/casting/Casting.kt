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

    companion object {
        // 진행 중인 CastingMinigame을 관리하는 집합
        val activeMinigames: MutableSet<Player> = mutableSetOf()
    }

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

        // 주조시작 버튼 슬롯(48, 49, 50) 배치
        val startButtonSlots = listOf(48, 49, 50)
        for (slot in startButtonSlots) {
            inventory.setItem(slot, createCastingStartButton())
        }

        // 플레이어에게 인벤토리 열기
        player.openInventory(inventory)

        // 임시 리스너 등록: 플레이어가 지정 슬롯에 빵(BREAD)을 올리면 미니게임 시작
        val tempListener = object : Listener {
            @EventHandler
            fun onInventoryClick(event: InventoryClickEvent) {
                if (event.whoClicked != player) return
                if (event.inventory != inventory) return

                // 40번 슬롯(재료 슬롯) 처리
                if (event.rawSlot == ingredientSlot) {
                    // 커서에 든 아이템이 없거나 빵이 아니라면 취소
                    val cursorItem = event.cursor
                    if (cursorItem.type != Material.BREAD) {
                        event.isCancelled = true
                        return
                    }
                    // 빵이 올려지면 기존 안내용 아이템 제거
                    if (event.currentItem?.type == Material.PAPER) {
                        inventory.setItem(ingredientSlot, null)
                    }
                    return
                }

                // 40번 슬롯: 재료 슬롯 (빵만 허용)
                // TODO : 추후 주괴 타입으로 변경
                if (event.rawSlot == ingredientSlot) {
                    // 플레이어가 빵을 올리려고 할 때, 빵이 아니라면 취소
                    val cursorItem = event.cursor
                    if (cursorItem.type != Material.BREAD) {
                        event.isCancelled = true
                        return
                    }
                    // 빵을 올리면 기존 안내용 아이템 제거
                    if (event.currentItem?.type == Material.PAPER && cursorItem.type == Material.BREAD) {
                        inventory.setItem(ingredientSlot, null)
                    }
                    return
                }

                // 주조시작 버튼 슬롯 클릭 처리
                if (event.rawSlot in startButtonSlots) {
                    event.isCancelled = true

                    // 이미 미니게임 진행 중이면 예외 처리
                    if (activeMinigames.contains(player)) {
                        player.sendMessage(Component.text("이미 주조 미니게임이 진행 중입니다.", NamedTextColor.RED))
                        return
                    }

                    // 40번 슬롯에 빵이 있어야 주조시작 가능
                    val breadItem = inventory.getItem(ingredientSlot)
                    if (breadItem == null || breadItem.type != Material.BREAD) {
                        player.sendMessage(Component.text("빵을 올려주세요", NamedTextColor.RED))
                        return
                    }

                    val breadForMinigame = handleIngredientSlotClick(inventory, ingredientSlot, player)
                    if (breadForMinigame != null) {
                        // 미니게임 시작 전 등록
                        activeMinigames.add(player)
                        HandlerList.unregisterAll(this)
                        // CastingMinigame 시작 (클릭 허용 시간: 3초)
                        val removalTime = 3.0
                        CastingMinigame(plugin, player, breadForMinigame, removalTime) { ingot, quality ->
                            player.sendMessage(Component.text("주조가 완료되었습니다! 생성된 주괴 품질: $quality", NamedTextColor.GREEN))
                            player.inventory.addItem(ingot)
                            // 미니게임 종료 후 진행중 상태 해제
                            activeMinigames.remove(player)
                        }.start()
                    }
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

    // 주조시작 버튼 아이템 생성 (STONE_BUTTON)
    private fun createCastingStartButton(): ItemStack {
        val item = ItemStack(Material.GRAY_DYE)
        val meta = item.itemMeta
        meta?.displayName(Component.text("주조시작", NamedTextColor.GREEN))
        item.itemMeta = meta
        return item
    }

    // 빵 아이템을 40번 슬롯에서 처리하여 CastingMinigame에 사용할 빵 한 개를 반환합니다.
    private fun handleIngredientSlotClick(inventory: Inventory, ingredientSlot: Int, player: Player): ItemStack? {
        val currentItem = inventory.getItem(ingredientSlot) ?: return null
        if (currentItem.type != Material.BREAD) return null

        val totalCount = currentItem.amount
        // CastingMinigame에 사용할 빵은 1개만 사용
        val breadForMinigame = currentItem.clone().apply { amount = 1 }
        // 남은 빵이 있다면 플레이어 인벤토리로 반환, 없으면 40번 슬롯 비움
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
 * - A 슬롯(예: 40번): 플레이어가 올린 빵(ingredientItem)을 표시합니다.
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
        var cycleCount: Int = 0,    // 4번의 확률 확인
        var burntStage: Int = 0,    // BURNT 상태의 removalTime 단계를 나타냄 (1~3)
        var timer: Int = 0,         // 틱 타이머 (20틱 = 1초)
        var startTick: Int = 0,     // 이벤트 시작 시점(틱)
        var cooldownDuration: Int = 0   // 불순물 발생 후 딜레이 (틱)
    )

    // 슬롯 상태 종류
    enum class SlotState {
        NONE,
        BURNT,
        FOAM,
        COOLDOWN,
        SUCCESS,
        FAIL
    }

    // 미니게임 이벤트 슬롯들을 관리할 맵
    private val castingSlots = mutableMapOf<Int, CastingSlot>()
    private var ticksElapsed = 0
    private var finishingCounter: Int = -1

    init {
        // A 슬롯에 플레이어가 올린 빵 재료 배치
        inventory.setItem(aSlot, ingredientItem)
        // B 슬롯 초기화: 각 슬롯에 무작위 시작 틱 할당 및 플레이스홀더 배치
        for (slot in bSlots) {
            val startTick = Random.nextInt(40, 81)
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
        val item = ItemStack(Material.GOLD_INGOT)
        val meta = item.itemMeta
        meta?.displayName(Component.text("주괴 [$quality]", NamedTextColor.GOLD))
        item.itemMeta = meta
        return item
    }

    // 불순물 단계별 아이템 생성 함수
    private fun createImpurityItem(stage: Int): ItemStack {
        val (material, displayName, textColor) = when (stage) {
            1 -> Triple(Material.WHITE_WOOL, "불순물 (흰색)", NamedTextColor.WHITE)
            2 -> Triple(Material.GRAY_WOOL, "불순물 (회색)", NamedTextColor.GRAY)
            3 -> Triple(Material.BLACK_WOOL, "불순물 (검정색)", NamedTextColor.BLACK)
            else -> Triple(Material.CHARCOAL, "불순물", NamedTextColor.RED)
        }
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(Component.text(displayName, textColor))
        item.itemMeta = meta
        return item
    }

    // run() 함수의 전체 처리를 세분화
    override fun run() {
        ticksElapsed++
        // 각 슬롯의 상태를 개별적으로 처리, 순서를 섞어서 랜덤한 위치에서 이벤트가 발생하도록 처리
        for ((slot, castingSlot) in castingSlots.entries.shuffled()) {
            processSlot(slot, castingSlot)
        }
        // 모든 슬롯의 최종 결과(성공 또는 실패)가 나온 후 종료 조건 체크
        checkFinishCondition()
    }

    // 슬롯별 상태 처리 분기점
    private fun processSlot(slot: Int, castingSlot: CastingSlot) {
        when (castingSlot.state) {
            SlotState.NONE -> processNoneState(slot, castingSlot)
            SlotState.BURNT -> processBurntState(slot, castingSlot)
            SlotState.FOAM -> processFoamState(slot, castingSlot)
            SlotState.COOLDOWN -> processCooldownState(slot, castingSlot)
            else -> { /* SUCCESS, FAIL 상태는 변화 없음 */ }
        }
    }

    // 상태가 NONE일 때 처리: 이벤트 시작 시점에 따라 불순물(첫 단계) 또는 FOAM 상태로 전환
    private fun processNoneState(slot: Int, castingSlot: CastingSlot) {
        castingSlot.timer++
        if (castingSlot.timer >= castingSlot.startTick) {
            val activeCount = castingSlots.values.count { it.state == SlotState.FOAM || it.state == SlotState.BURNT }
            if (activeCount < 4) {  // 동시에 활성 상태가 4개 미만일 때만 전환 시도
                if (Random.nextBoolean()) {
                    castingSlot.state = SlotState.BURNT
                    castingSlot.burntStage = 1
                    castingSlot.timer = 0
                    inventory.setItem(slot, createImpurityItem(castingSlot.burntStage))
                } else {
                    castingSlot.state = SlotState.FOAM
                    castingSlot.timer = 0
                    inventory.setItem(slot, createFoamItem())
                }
            }
        }
    }

    // 상태가 FOAM일 때 처리: 타이머 만료 후 불순물 이벤트 발생 여부에 따라 상태 전환
    private fun processFoamState(slot: Int, castingSlot: CastingSlot) {
        castingSlot.timer++
        if (castingSlot.timer >= 40 && castingSlot.state == SlotState.FOAM) {
            inventory.setItem(slot, createPlaceholderItem())
            if (castingSlot.cycleCount < 4 && Random.nextDouble() < 0.8) {
                castingSlot.state = SlotState.NONE
                castingSlot.timer = 0
                castingSlot.burntStage = 1
                castingSlot.cycleCount++
                inventory.setItem(slot, createImpurityItem(castingSlot.burntStage))
            } else {
                castingSlot.state = SlotState.SUCCESS
                inventory.setItem(slot, createMarkItem("성공"))
            }
        }
    }

    // 상태가 BURNT일 때 처리: 일정 시간 경과 후 불순물 단계를 증가시키거나 실패 처리
    private fun processBurntState(slot: Int, castingSlot: CastingSlot) {
        castingSlot.timer++
        val threshold = getRemovalTimeForStage(castingSlot.burntStage)
        if (castingSlot.timer >= threshold && castingSlot.state == SlotState.BURNT) {
            if (castingSlot.burntStage < 3) {
                castingSlot.timer = 0
                castingSlot.burntStage++
                inventory.setItem(slot, createImpurityItem(castingSlot.burntStage))
            } else {
                castingSlot.state = SlotState.FAIL
                inventory.setItem(slot, createMarkItem("실패"))
            }
        }
    }

    // 상태가 COOLDOWN일 때 처리: 딜레이 후 불순물 재발생 여부를 결정
    private fun processCooldownState(slot: Int, castingSlot: CastingSlot) {
        castingSlot.timer++
        if (castingSlot.timer >= castingSlot.cooldownDuration) {
            if (castingSlot.cycleCount < 3 && Random.nextBoolean()) {
                castingSlot.state = SlotState.BURNT
                castingSlot.timer = 0
                castingSlot.cycleCount++
                inventory.setItem(slot, createImpurityItem(castingSlot.cycleCount))
            } else {
                castingSlot.state = SlotState.FAIL
                inventory.setItem(slot, createMarkItem("실패"))
            }
        }
    }

    // helper 함수: 단계별 removalTime (틱 단위)를 반환
    private fun getRemovalTimeForStage(stage: Int): Int {
        return when(stage) {
            1 -> (removalTime * 20 / 3).toInt()
            2 -> (removalTime * 20 / 3).toInt()
            3 -> (removalTime * 20 / 3).toInt()
            else -> (removalTime * 20).toInt()
        }
    }

    // 모든 슬롯의 최종 상태(성공 또는 실패)가 결정된 후 2초(40틱) 뒤에 게임 종료 여부를 체크하는 함수
    private fun checkFinishCondition() {
        val allFinal = castingSlots.values.all { it.state == SlotState.SUCCESS || it.state == SlotState.FAIL }
        if (allFinal) {
            if (finishingCounter == -1) {
                finishingCounter = 40  // 40틱 = 2초
            } else {
                finishingCounter--
            }
            if (finishingCounter <= 0) {
                cancel()
                finishCasting()
            }
        } else {
            finishingCounter = -1
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
        this.runTaskTimer(plugin, 20L, 1L)
    }
}
