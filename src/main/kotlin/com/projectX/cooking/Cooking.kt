package com.projectX.cooking

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

class Cooking(private val plugin: Plugin) : Listener {
    private val inventoryMap: MutableMap<Inventory, Location> = mutableMapOf()
    private val activeCookings: MutableMap<Location, CookingCompletenessGauge> = mutableMapOf()

    @EventHandler
    fun onFurnitureInteract(event: NexoFurnitureInteractEvent) {
        val baseEntity = event.baseEntity
        event.player.sendMessage("위치0 : ${baseEntity.location}")
        event.player.sendMessage("${baseEntity.name}와 상호작용 했습니다.")
        if (baseEntity.name.contains("Chopping Board")) {
            event.player.sendMessage("위치1 : ${baseEntity.location}")
            openCookingInventory(event.player, baseEntity.location)
        }
    }

    fun openCookingInventory(player: Player, location: Location) {
        player.sendMessage("pos0 : $location")
        if (activeCookings.containsKey(location)) {
            return
        }

        player.sendMessage("pos1 : $location")

        val title = Component.text("요리하기")
        val inventory = Bukkit.createInventory(null, 54, title)

        // TODO: Material.GRAY_DYE -> 버튼 이미지 변환
        val buttonItem = ItemStack(Material.GRAY_DYE)
        val meta = buttonItem.itemMeta
        meta?.let {
            it.displayName(Component.text("요리하기", NamedTextColor.WHITE))
            buttonItem.itemMeta = it
        }

        inventory.setItem(52, buttonItem)
        inventory.setItem(53, buttonItem)

        player.openInventory(inventory)
        player.sendMessage("pos2 : $location")
        inventoryMap[inventory] = location
        player.sendMessage("pos3 : ${inventoryMap[inventory]}")
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val inventory = event.inventory
        val location = inventoryMap[inventory] ?: return
        val player = event.whoClicked as Player
        val clickedSlot = event.rawSlot

        player.sendMessage("실제 위치 : $location")

        if (clickedSlot == 52 || clickedSlot == 53) {
            event.isCancelled = true

            if (activeCookings.containsKey(location)) {
                player.sendMessage("이미 요리를 진행 중입니다.")
                return
            }

            val result = processCooking(inventory, player)
            if (result != null) {
                val cookingCompletenessGauge = CookingCompletenessGauge(player, result, location) {
                    activeCookings.remove(location)
                }
                activeCookings[location] = cookingCompletenessGauge
                Bukkit.getPluginManager().registerEvents(cookingCompletenessGauge, plugin)
                cookingCompletenessGauge.runTaskTimer(plugin, 0L, 7L)
                player.closeInventory()
            } else {
                player.sendMessage("레시피가 올바르지 않습니다.")
            }
        }
    }

    private fun processCooking(inventory: Inventory, player: Player): ItemStack? {
        val requiredItems = mapOf(
            Material.APPLE to 2,
            Material.SUGAR to 1,
        )

        val ingredientCount = countIngredients(inventory)

        return if (hasRequiredIngredients(ingredientCount, requiredItems)) {
            consumeIngredients(inventory, requiredItems)    // 재료 소모
            returnRemainingIngredients(inventory, player)   // 남은 재료 반환
            createCustomItem()  // 결과물 생성
        } else {
            null
        }
    }

    private fun countIngredients(inventory: Inventory): MutableMap<Material, Int> {
        val ingredientCount = mutableMapOf<Material, Int>()
        for (i in 0 until 52) {
            val item = inventory.getItem(i)
            if (item != null) {
                val material = item.type
                val count = item.amount
                ingredientCount[material] = ingredientCount.getOrDefault(material, 0) + count
            }
        }
        return ingredientCount
    }

    private fun hasRequiredIngredients(
        ingredientCount: Map<Material, Int>,
        requiredItems: Map<Material, Int>
    ): Boolean {
        return requiredItems.all { (material, count) ->
            ingredientCount.getOrDefault(material, 0) >= count
        }
    }

    private fun consumeIngredients(inventory: Inventory, requiredItems: Map<Material, Int>) {
        for ((material, requiredCount) in requiredItems) {
            var remaining = requiredCount
            for (i in 0 until 52) {
                val item = inventory.getItem(i)
                if (item != null && item.type == material) {
                    if (item.amount > remaining) {
                        item.amount -= remaining
                        remaining = 0
                    } else {
                        remaining -= item.amount
                        inventory.clear(i)
                    }
                    if (remaining == 0) break
                }
            }
        }
    }

    private fun returnRemainingIngredients(inventory: Inventory, player: Player) {
        for (i in 0 until 52) { // 요리하기 버튼을 제외한 슬롯
            val item = inventory.getItem(i)
            if (item != null) {
                // 플레이어의 인벤토리에 아이템 추가 시 남는 아이템 확인
                val remainingItems = player.inventory.addItem(item)
                if (remainingItems.isNotEmpty()) {
                    // 인벤토리에 공간이 부족하여 남은 아이템이 있을 경우, 해당 아이템을 드롭
                    for (remainingItem in remainingItems.values) {
                        player.world.dropItemNaturally(player.location, remainingItem)
                    }
                }
                inventory.clear(i) // 인벤토리 슬롯 비우기
            }
        }
    }

    private fun createCustomItem(): ItemStack {
        val item = ItemStack(Material.GOLDEN_APPLE)
        val meta = item.itemMeta
        meta?.let {
            // TODO: recipe에 따라 결과물 생성
            val displayName = Component.text("특제 요리", NamedTextColor.GOLD)
            it.displayName(displayName)
            item.itemMeta = it
        }
        return item
    }

    fun isCooking(player: Player): Boolean {
        return activeCookings.values.any { it.player == player }
    }

    fun isDeviceCooking(location: Location): Boolean {
        return activeCookings.containsKey(location)
    }
}