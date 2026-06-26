package com.horrorgame.awakening.game2d

import kotlin.math.*
import kotlin.random.Random

/**
 * Central game state: player, monsters, items, zone transitions.
 */
class GameWorld {

    var player = Player(15f * 32, 35f * 32)
    val monsters = mutableListOf<Monster>()
    val worldItems = mutableListOf<WorldItemEntity>()
    var currentZone: MapData.Zone
    var gameTime = 0
    var isGameOver = false
    var gameOverMessage = ""
    var isVictory = false
    var messages = mutableListOf<GameMessage>()
    var screenShake = 0
    var flashAlpha = 0f

    data class WorldItemEntity(
        val type: MapData.ItemType,
        var x: Float,
        var y: Float,
        var collected: Boolean = false
    )
    data class GameMessage(val text: String, var timer: Int, val isDanger: Boolean = false)

    init {
        currentZone = MapData.zones["outdoor"]!!
        spawnZoneEntities()
        player.x = currentZone.playerSpawn.first * MapData.TILE_SIZE.toFloat()
        player.y = currentZone.playerSpawn.second * MapData.TILE_SIZE.toFloat()
        player.currentZone = currentZone.id
    }

    fun update(joystickDx: Float, joystickDy: Float, attackPressed: Boolean): Boolean {
        if (isGameOver || screenShake > 0 || flashAlpha > 0) return false

        gameTime++
        var zoneChanged = false

        // Update player
        player.update()
        if (player.attackCooldown <= 0 && attackPressed) {
            attack()
        }

        // Player movement
        if (joystickDx != 0f || joystickDy != 0f) {
            val len = sqrt(joystickDx * joystickDx + joystickDy * joystickDy)
            val nx = joystickDx / len
            val ny = joystickDy / len
            player.facingAngle = atan2(ny.toDouble(), nx.toDouble()).toFloat()
            player.isMoving = true

            val newX = player.x + nx * player.speed
            val newY = player.y + ny * player.speed

            // Tile collision
            val canMoveX = isWalkable(newX, player.y)
            val canMoveY = isWalkable(player.x, newY)
            if (canMoveX) player.x = newX
            if (canMoveY) player.y = newY

            // Check exit transitions
            val tx = (player.x / MapData.TILE_SIZE).toInt()
            val ty = (player.y / MapData.TILE_SIZE).toInt()
            currentZone.exits[tx to ty]?.let { exit ->
                changeZone(exit.zoneId, exit.toX, exit.toY)
                zoneChanged = true
            }

            // Collect items
            worldItems.filter { !it.collected }.forEach { item ->
                val dist = sqrt((player.x-item.x)*(player.x-item.x) + (player.y-item.y)*(player.y-item.y))
                if (dist < 24f) {
                    item.collected = true
                    player.addItem(item.type)
                    val name = itemName(item.type)
                    messages.add(GameMessage("+ $name", 120))
                }
            }
        } else {
            player.isMoving = false
        }

        // Update monsters
        for (m in monsters) {
            m.update(player.x, player.y, currentZone.tiles, MapData.TILE_SIZE)

            // Monster attacks player
            if (m.state == Monster.State.ATTACK && m.attackTimer == 30) {
                player.takeDamage(m.damage)
                player.loseSanity(5)
                screenShake = 10
                flashAlpha = 0.6f
                messages.add(GameMessage("-${m.damage} HP!", 90, true))
            }
        }

        // Sanity drain in dark zones
        if (currentZone.isDark && !player.hasFlashlight) {
            if (gameTime % 120 == 0) player.loseSanity(2)
        }
        if (player.hasFlashlight && player.isMoving && player.flashlightBattery <= 0) {
            player.hasFlashlight = false
            messages.add(GameMessage("Батарейки сели!", 120, true))
        }

        // Update messages
        messages.forEach { it.timer-- }
        messages.removeAll { it.timer <= 0 }

        if (screenShake > 0) screenShake--
        if (flashAlpha > 0) flashAlpha = (flashAlpha - 0.05f).coerceAtLeast(0f)

        // Check game over
        if (!player.isAlive) {
            isGameOver = true
            gameOverMessage = if (player.isDead) "ВЫ ПОГИБЛИ" else "РАССУДОК УТРАЧЕН"
            isVictory = false
        }

        // Check darkness sanity loss
        if (currentZone.isDark && gameTime % 300 == 0 && !player.hasFlashlight) {
            player.loseSanity(5)
            messages.add(GameMessage("Тьма сводит с ума!", 120, true))
        }

        return zoneChanged
    }

    private fun attack() {
        if (!player.hasRevolver || player.ammo <= 0) {
            messages.add(GameMessage("Нет оружия!", 60))
            return
        }
        player.ammo--
        player.attackCooldown = 25

        // Find closest monster in attack range
        val attackRange = 100f
        var closest: Monster? = null
        var closestDist = attackRange
        for (m in monsters) {
            if (!m.isAlive) continue
            val dx = m.x - player.x; val dy = m.y - player.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < closestDist) {
                closestDist = dist; closest = m
            }
        }

        if (closest != null) {
            val killed = closest!!.takeDamage()
            if (killed) {
                messages.add(GameMessage("Убит!", 120))
                // Boss kill = victory
                if (closest!!.type == MapData.MonsterType.BOSS) {
                    isGameOver = true
                    isVictory = true
                    gameOverMessage = "ТВАРЬ УНИЧТОЖЕНА!\nВЫ ВЫБРАЛИСЬ!"
                }
            } else {
                messages.add(GameMessage("Попадание!", 60))
            }
            screenShake = 6
        } else {
            messages.add(GameMessage("Мимо!", 60))
        }
        flashAlpha = 0.3f
    }

    private fun isWalkable(x: Float, y: Float): Boolean {
        val tx = (x / MapData.TILE_SIZE).toInt().coerceIn(0, currentZone.width - 1)
        val ty = (y / MapData.TILE_SIZE).toInt().coerceIn(0, currentZone.height - 1)
        val tile = currentZone.tiles[ty][tx]
        return when (tile) {
            MapData.WALL, MapData.TREE, MapData.WATER -> false
            MapData.DOOR_LOCKED -> false
            else -> true
        }
    }

    fun changeZone(zoneId: String, spawnX: Int, spawnY: Int) {
        val zone = MapData.zones[zoneId] ?: return
        if (zoneId == "church" && !player.hasKey("church")) {
            messages.add(GameMessage("Дверь заперта. Нужен ключ.", 120))
            return
        }
        if (zoneId == "hospital_bsmt" && !player.hasKey("bunker") &&
            currentZone.id != "hospital_1f") {
            messages.add(GameMessage("Люк заперт. Нужен ключ от бункера.", 120))
            return
        }

        currentZone = zone
        player.currentZone = zoneId
        player.x = spawnX * MapData.TILE_SIZE.toFloat() + MapData.TILE_SIZE / 2f
        player.y = spawnY * MapData.TILE_SIZE.toFloat() + MapData.TILE_SIZE / 2f
        spawnZoneEntities()
        messages.add(GameMessage(zone.name, 120))
    }

    private fun spawnZoneEntities() {
        monsters.clear()
        worldItems.clear()

        currentZone.monsterSpawns.forEach { spawn ->
            monsters.add(Monster(
                type = spawn.type,
                x = spawn.x * MapData.TILE_SIZE.toFloat(),
                y = spawn.y * MapData.TILE_SIZE.toFloat(),
                patrolOriginX = spawn.x * MapData.TILE_SIZE.toFloat(),
                patrolOriginY = spawn.y * MapData.TILE_SIZE.toFloat(),
                patrolRadius = spawn.patrolRadius
            ))
        }

        currentZone.items.forEach { item ->
            worldItems.add(WorldItemEntity(
                type = item.type,
                x = item.x * MapData.TILE_SIZE.toFloat() + MapData.TILE_SIZE / 2f,
                y = item.y * MapData.TILE_SIZE.toFloat() + MapData.TILE_SIZE / 2f
            ))
        }
    }

    private fun itemName(type: MapData.ItemType): String = when(type) {
        MapData.ItemType.MEDKIT -> "Аптечка (+30 HP)"
        MapData.ItemType.AMMO -> "Патроны (+3)"
        MapData.ItemType.REVOLVER -> "Револьвер"
        MapData.ItemType.FLASHLIGHT -> "Фонарик"
        MapData.ItemType.KEY_HOSPITAL -> "Ключ от больницы"
        MapData.ItemType.KEY_CHURCH -> "Ключ от церкви"
        MapData.ItemType.KEY_BUNKER -> "Ключ от бункера"
        MapData.ItemType.BATTERY -> "Батарейки"
        MapData.ItemType.DOCUMENT -> "Документ"
        MapData.ItemType.CROSS -> "Крест (+20 Sanity)"
        MapData.ItemType.GASOLINE -> "Канистра"
        MapData.ItemType.ADRENALINE -> "Адреналин (+20 HP, +30 Sanity)"
    }
}
