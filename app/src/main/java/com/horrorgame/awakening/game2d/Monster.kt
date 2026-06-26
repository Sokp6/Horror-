package com.horrorgame.awakening.game2d

import kotlin.math.*
import kotlin.random.Random

/**
 * Monster entity with AI: patrol, chase, attack.
 */
class Monster(
    val type: MapData.MonsterType,
    var x: Float,
    var y: Float,
    val patrolOriginX: Float,
    val patrolOriginY: Float,
    val patrolRadius: Int
) {
    var state = State.PATROL
    var patrolTargetX = x
    var patrolTargetY = y
    var health: Int = when(type) {
        MapData.MonsterType.SHADOW -> 2
        MapData.MonsterType.SCREAMER -> 1
        MapData.MonsterType.WRAITH -> 3
        MapData.MonsterType.BOSS -> 8
    }
    val maxHealth = health
    var attackTimer = 0
    var stunTimer = 0
    var speed: Float = when(type) {
        MapData.MonsterType.SHADOW -> 1.2f
        MapData.MonsterType.SCREAMER -> 2.5f
        MapData.MonsterType.WRAITH -> 1.5f
        MapData.MonsterType.BOSS -> 1.8f
    }
    val size: Float = when(type) {
        MapData.MonsterType.BOSS -> 32f
        else -> 22f
    }
    val damage: Int = when(type) {
        MapData.MonsterType.SHADOW -> 8
        MapData.MonsterType.SCREAMER -> 15
        MapData.MonsterType.WRAITH -> 12
        MapData.MonsterType.BOSS -> 25
    }
    val detectionRange: Float = when(type) {
        MapData.MonsterType.BOSS -> 250f
        MapData.MonsterType.WRAITH -> 180f
        else -> 140f
    }

    enum class State { PATROL, CHASE, ATTACK, STUNNED, DEAD }
    val isAlive get() = state != State.DEAD

    fun update(playerX: Float, playerY: Float, tiles: Array<IntArray>, tileSize: Int) {
        if (!isAlive) return

        if (stunTimer > 0) {
            stunTimer--
            if (stunTimer == 0) state = State.PATROL
            else return
        }

        val dx = playerX - x
        val dy = playerY - y
        val distToPlayer = sqrt(dx * dx + dy * dy)

        when (state) {
            State.PATROL -> {
                // Move towards patrol target
                val pdx = patrolTargetX - x
                val pdy = patrolTargetY - y
                val pdist = sqrt(pdx * pdx + pdy * pdy)

                if (pdist < 5f) {
                    // Pick new patrol target
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val radius = Random.nextFloat() * patrolRadius * tileSize
                    patrolTargetX = (patrolOriginX + cos(angle) * radius).coerceIn(
                        tileSize.toFloat(), (tiles[0].size - 1) * tileSize.toFloat())
                    patrolTargetY = (patrolOriginY + sin(angle) * radius).coerceIn(
                        tileSize.toFloat(), (tiles.size - 1) * tileSize.toFloat())
                } else {
                    moveTowards(patrolTargetX, patrolTargetY, speed * 0.5f, tiles, tileSize)
                }

                // Check if player detected
                if (distToPlayer < detectionRange) {
                    state = State.CHASE
                }
            }
            State.CHASE -> {
                if (distToPlayer > detectionRange * 1.5f) {
                    state = State.PATROL
                    patrolTargetX = x; patrolTargetY = y
                } else if (distToPlayer < size + 15f) {
                    state = State.ATTACK
                    attackTimer = 30
                } else {
                    moveTowards(playerX, playerY, speed, tiles, tileSize)
                }
            }
            State.ATTACK -> {
                attackTimer--
                if (attackTimer <= 0 && distToPlayer < size + 20f) {
                    state = State.CHASE
                }
                if (distToPlayer > size + 25f) {
                    state = State.CHASE
                }
            }
            else -> {}
        }
    }

    private fun moveTowards(tx: Float, ty: Float, spd: Float, tiles: Array<IntArray>, ts: Int) {
        val dx = tx - x
        val dy = ty - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1f) return

        val nx = x + (dx / dist) * spd
        val ny = y + (dy / dist) * spd

        // Collision check - only update if new position is walkable
        val tileX = (nx / ts).toInt().coerceIn(0, tiles[0].size - 1)
        val tileY = (ny / ts).toInt().coerceIn(0, tiles.size - 1)
        val tile = tiles[tileY][tileX]

        if (tile != MapData.WALL && tile != MapData.TREE && tile != MapData.WATER &&
            tile != MapData.DOOR_LOCKED) {
            x = nx; y = ny
        } else {
            // Try sliding along one axis
            val tx2 = (x + (dx / dist) * spd / ts).toInt().coerceIn(0, tiles[0].size - 1)
            val ty2 = (y / ts).toInt().coerceIn(0, tiles.size - 1)
            val tx3 = (x / ts).toInt().coerceIn(0, tiles[0].size - 1)
            val ty3 = ((y + (dy / dist) * spd) / ts).toInt().coerceIn(0, tiles.size - 1)

            if (tiles[ty2][tx2] != MapData.WALL && tiles[ty2][tx2] != MapData.TREE) {
                x = nx
            }
            if (tiles[ty3][tx3] != MapData.WALL && tiles[ty3][tx3] != MapData.TREE) {
                y = ny
            }
        }
    }

    fun takeDamage(): Boolean {
        if (!isAlive) return false
        health--
        if (health <= 0) {
            state = State.DEAD
            return true // killed
        }
        state = State.STUNNED
        stunTimer = 45
        return false
    }
}
