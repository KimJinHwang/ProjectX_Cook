package com.projectX.cooking

import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent

class CookingCommandExecutor(private val cooking: Cooking) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.size != 3) {
            sender.sendMessage("사용법: /open_cooking_interface <x> <y> <z>")
            return true
        }

        // 플레이어만 사용 가능
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return false
        }
        val player: Player = sender

        // 플레이스홀더가 들어간 인자는 치환하는 헬퍼 함수
        fun processArg(arg: String, default: Int): String {
            // 플레이스홀더 예시: "<#block.x>" 같은 경우
            return if (arg.startsWith("<#")) default.toString() else arg
        }

        // 여기서는 플레이어의 현재 블록 좌표를 기본값으로 사용
        val defaultX = player.location.blockX
        val defaultY = player.location.blockY
        val defaultZ = player.location.blockZ

        // 각 인자에 대해 플레이스홀더 치환 적용
        val xStr = processArg(args[0], defaultX)
        val yStr = processArg(args[1], defaultY)
        val zStr = processArg(args[2], defaultZ)

        sender.sendMessage(args[0])

        // 치환 후 숫자로 변환
        val x = xStr.toDoubleOrNull()
        val y = yStr.toDoubleOrNull()
        val z = zStr.toDoubleOrNull()

        if (x == null || y == null || z == null) {
            sender.sendMessage("좌표 값이 올바르지 않습니다.")
            return true
        }

        val location = Location(player.world, x, y, z)

        if (cooking.isCooking(sender) || cooking.isDeviceCooking(location)) {
            sender.sendMessage("이미 요리 중입니다.")
            return true
        }
        cooking.openCookingInventory(sender, location)
        return true
    }
}
