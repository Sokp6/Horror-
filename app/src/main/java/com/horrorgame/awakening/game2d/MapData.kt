package com.horrorgame.awakening.game2d

/**
 * Tile maps for the open world and all interior zones.
 * 0=ROAD 1=WALL 2=FLOOR 3=GRASS 4=TREE 5=DOOR 6=DOOR_LOCKED 7=EXIT 8=WATER 9=GRAVE
 */
object MapData {

    const val TILE_SIZE = 32
    const val ROAD = 0; const val WALL = 1; const val FLOOR = 2
    const val GRASS = 3; const val TREE = 4; const val DOOR = 5
    const val DOOR_LOCKED = 6; const val EXIT = 7; const val WATER = 8
    const val GRAVE = 9

    data class Zone(
        val id: String,
        val name: String,
        val width: Int,
        val height: Int,
        val tiles: Array<IntArray>,
        val exits: Map<Pair<Int,Int>, ExitTarget> = emptyMap(),
        val items: List<WorldItem> = emptyList(),
        val monsterSpawns: List<MonsterSpawn> = emptyList(),
        val playerSpawn: Pair<Int,Int> = 5 to 5,
        val isDark: Boolean = false,
        val ambientColor: Int = 0xFF1A1A2E.toInt()
    )

    data class ExitTarget(val zoneId: String, val toX: Int, val toY: Int)
    data class WorldItem(val type: ItemType, val x: Int, val y: Int)
    data class MonsterSpawn(val type: MonsterType, val x: Int, val y: Int, val patrolRadius: Int = 5)

    enum class ItemType {
        MEDKIT, AMMO, REVOLVER, FLASHLIGHT, KEY_HOSPITAL, KEY_CHURCH, KEY_BUNKER,
        BATTERY, DOCUMENT, CROSS, GASOLINE, ADRENALINE
    }

    enum class MonsterType { SHADOW, SCREAMER, WRAITH, BOSS }

    // ==================== ZONES ====================

    val zones: Map<String, Zone> = mapOf(
        "outdoor" to buildOutdoor(),
        "hospital_1f" to buildHospital1F(),
        "hospital_2f" to buildHospital2F(),
        "hospital_bsmt" to buildBasement(),
        "church" to buildChurch(),
        "morgue" to buildMorgue(),
        "forest" to buildForest()
    )

    // =============== OUTDOOR COMPOUND (80 x 60) ===============
    private fun buildOutdoor(): Zone {
        val w = 80; val h = 60
        val t = Array(h) { IntArray(w) { GRASS } }

        // Draw roads
        for (x in 0 until w) { t[30][x] = ROAD; t[35][x] = ROAD } // Main horizontal roads
        for (y in 0 until h) { t[y][20] = ROAD; t[y][55] = ROAD } // Main vertical roads

        // Hospital building outline (25-45, 5-20)
        for (y in 5..20) { for (x in 25..45) { t[y][x] = FLOOR } }
        for (y in 5..20) { t[y][25] = WALL; t[y][45] = WALL }
        for (x in 25..45) { t[5][x] = WALL; t[20][x] = WALL }
        t[20][35] = DOOR // Hospital entrance
        t[35][31] = EXIT // Entry point -> hospital_1f at (5,18)

        // Church (50-65, 5-18)
        for (y in 5..18) { for (x in 50..65) { t[y][x] = FLOOR } }
        for (y in 5..18) { t[y][50] = WALL; t[y][65] = WALL }
        for (x in 50..65) { t[5][x] = WALL; t[18][x] = WALL }
        t[18][57] = DOOR_LOCKED // Church door - needs key
        t[18][58] = DOOR_LOCKED

        // Morgue small building (5-15, 5-15)
        for (y in 5..15) { for (x in 5..15) { t[y][x] = FLOOR } }
        for (y in 5..15) { t[y][5] = WALL; t[y][15] = WALL }
        for (x in 5..15) { t[5][x] = WALL; t[15][x] = WALL }
        t[15][10] = DOOR

        // Graveyard area (55-75, 35-55)
        for (y in 35..55) { for (x in 55..75) {
            t[y][x] = if ((x+y) % 6 == 0) GRAVE else GRASS
        }}
        // Add broken walls around graveyard
        for (y in 35..55) { t[y][55] = if (y%5==0) WALL else GRASS }

        // Forest area top-right (60-79, 0-25)
        for (y in 0..25) { for (x in 60..79) { t[y][x] = if ((x*y) % 4 == 0) TREE else GRASS } }
        t[10][70] = EXIT // -> forest zone

        // Forest bottom area (65-79, 40-59)
        for (y in 40..59) { for (x in 65..79) { t[y][x] = if ((x+y) % 5 == 0) TREE else GRASS } }

        // Water/pond (40-50, 45-52)
        for (y in 45..52) { for (x in 40..50) { t[y][x] = WATER } }
        for (x in 40..50) { t[44][x] = GRASS; t[53][x] = GRASS }

        // Scattered trees
        for (i in 0..60) {
            val tx = (i * 17 + 3) % w; val ty = (i * 13 + 7) % h
            if (t[ty][tx] == GRASS) t[ty][tx] = TREE
        }

        // Bunker entrance in forest (hidden)
        t[48][70] = GRASS // Path to bunker
        t[50][72] = EXIT // -> hospital_bsmt zone

        return Zone(
            id = "outdoor", name = "Заброшенная территория",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (35 to 31) to ExitTarget("hospital_1f", 5, 18),
                (10 to 70) to ExitTarget("forest", 5, 15),
                (50 to 72) to ExitTarget("hospital_bsmt", 14, 24)
            ),
            items = listOf(
                WorldItem(ItemType.MEDKIT, 15, 32),
                WorldItem(ItemType.MEDKIT, 42, 33),
                WorldItem(ItemType.BATTERY, 28, 35),
                WorldItem(ItemType.AMMO, 60, 33),
                WorldItem(ItemType.KEY_CHURCH, 10, 35),
                WorldItem(ItemType.GASOLINE, 55, 45),
                WorldItem(ItemType.AMMO, 65, 50),
                WorldItem(ItemType.DOCUMENT, 30, 40),
                WorldItem(ItemType.MEDKIT, 38, 55)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SHADOW, 20, 25, 10),
                MonsterSpawn(MonsterType.SHADOW, 55, 30, 8),
                MonsterSpawn(MonsterType.SCREAMER, 70, 15, 5),
                MonsterSpawn(MonsterType.WRAITH, 60, 45, 12),
                MonsterSpawn(MonsterType.SHADOW, 10, 50, 8)
            ),
            playerSpawn = 15 to 35
        )
    }

    // =============== HOSPITAL GROUND FLOOR (40 x 30) ===============
    private fun buildHospital1F(): Zone {
        val w = 40; val h = 30
        val t = Array(h) { IntArray(w) { FLOOR } }

        // Outer walls
        for (y in 0 until h) { t[y][0] = WALL; t[y][w-1] = WALL }
        for (x in 0 until w) { t[0][x] = WALL; t[h-1][x] = WALL }

        // Entrance
        t[h-1][20] = DOOR; t[h-1][19] = DOOR

        // Main corridor
        for (x in 0 until w) { t[12][x] = FLOOR }
        for (x in 0 until w) { t[13][x] = ROAD } // Central corridor

        // Vertical corridors
        for (y in 0 until h) { t[y][10] = ROAD; t[y][30] = ROAD }

        // Room dividers (horizontal walls)
        for (x in 0..9) {
            t[5][x] = WALL; t[20][x] = WALL // Left rooms
        }
        for (x in 11..29) {
            t[23][x] = WALL // Right rooms
        }
        // Vertical room walls
        for (y in 6..12) { t[y][5] = WALL }
        for (y in 13..19) { t[y][25] = WALL }

        // Doors to rooms
        t[5][8] = DOOR; t[20][8] = DOOR
        t[12][11] = DOOR; t[12][31] = DOOR

        // Locked door to storage
        t[23][22] = DOOR_LOCKED; t[23][23] = DOOR_LOCKED

        // Stairs up
        t[1][10] = EXIT // -> hospital_2f
        // Stairs to basement
        t[1][30] = EXIT // -> hospital_bsmt (locked without key)

        return Zone(
            id = "hospital_1f", name = "Больница — 1 этаж",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (h-1 to 19) to ExitTarget("outdoor", 35, 20),
                (h-1 to 20) to ExitTarget("outdoor", 36, 20),
                (1 to 10) to ExitTarget("hospital_2f", 28, 10),
                (1 to 30) to ExitTarget("hospital_bsmt", 20, 1)
            ),
            items = listOf(
                WorldItem(ItemType.MEDKIT, 5, 5),
                WorldItem(ItemType.FLASHLIGHT, 15, 3),
                WorldItem(ItemType.AMMO, 32, 8),
                WorldItem(ItemType.KEY_BUNKER, 35, 20),
                WorldItem(ItemType.DOCUMENT, 2, 15),
                WorldItem(ItemType.REVOLVER, 25, 25),
                WorldItem(ItemType.BATTERY, 33, 28)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SHADOW, 20, 15, 8),
                MonsterSpawn(MonsterType.SCREAMER, 32, 20, 4),
                MonsterSpawn(MonsterType.SHADOW, 5, 10, 6)
            ),
            playerSpawn = 5 to 18,
            isDark = true
        )
    }

    // =============== HOSPITAL 2ND FLOOR (40 x 30) ===============
    private fun buildHospital2F(): Zone {
        val w = 40; val h = 30
        val t = Array(h) { IntArray(w) { FLOOR } }

        for (y in 0 until h) { t[y][0] = WALL; t[y][w-1] = WALL }
        for (x in 0 until w) { t[0][x] = WALL; t[h-1][x] = WALL }

        // Corridors
        for (x in 0 until w) { t[15][x] = ROAD }
        for (y in 0 until h) { t[y][15] = ROAD; t[y][25] = ROAD }

        // Room walls
        for (y in 0..14) { t[y][10] = WALL }
        for (x in 16..39) { t[8][x] = WALL }
        for (x in 0..14) { t[22][x] = WALL }

        // Doors
        t[15][5] = DOOR; t[15][20] = DOOR; t[8][28] = DOOR
        t[3][15] = DOOR; t[22][8] = DOOR

        // Locked office
        t[3][26] = DOOR_LOCKED

        // Stairs down
        t[h-1][10] = EXIT // -> hospital_1f

        return Zone(
            id = "hospital_2f", name = "Больница — 2 этаж",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (h-1 to 10) to ExitTarget("hospital_1f", 1, 10)
            ),
            items = listOf(
                WorldItem(ItemType.MEDKIT, 12, 3),
                WorldItem(ItemType.DOCUMENT, 5, 20),
                WorldItem(ItemType.AMMO, 28, 5),
                WorldItem(ItemType.CROSS, 35, 10),
                WorldItem(ItemType.ADRENALINE, 3, 25),
                WorldItem(ItemType.KEY_HOSPITAL, 28, 28)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SHADOW, 20, 10, 7),
                MonsterSpawn(MonsterType.WRAITH, 30, 20, 5),
                MonsterSpawn(MonsterType.SCREAMER, 5, 5, 3)
            ),
            playerSpawn = 28 to 10,
            isDark = true
        )
    }

    // =============== BASEMENT / BUNKER (30 x 25) ===============
    private fun buildBasement(): Zone {
        val w = 30; val h = 25
        val t = Array(h) { IntArray(w) { FLOOR } }

        for (y in 0 until h) { t[y][0] = WALL; t[y][w-1] = WALL }
        for (x in 0 until w) { t[0][x] = WALL; t[h-1][x] = WALL }

        // Corridor through center
        for (x in 5..25) { t[12][x] = ROAD }

        // Room walls
        for (x in 0..15) { t[5][x] = WALL }
        for (x in 0..w-1) { t[20][x] = WALL }
        for (y in 13..19) { t[y][15] = WALL }

        // Doors
        t[12][3] = DOOR; t[12][18] = DOOR; t[5][10] = DOOR
        t[20][10] = DOOR_LOCKED; t[20][11] = DOOR_LOCKED // Boss room

        // Ritual room
        for (y in 21..h-2) { for (x in 5..25) {
            t[y][x] = if ((x+y) % 3 == 0) GRAVE else FLOOR
        }}

        return Zone(
            id = "hospital_bsmt", name = "Подвал / Бункер",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (1 to 20) to ExitTarget("hospital_1f", 1, 30),
                (h-1 to 15) to ExitTarget("outdoor", 50, 72)
            ),
            items = listOf(
                WorldItem(ItemType.AMMO, 8, 3),
                WorldItem(ItemType.MEDKIT, 20, 3),
                WorldItem(ItemType.ADRENALINE, 3, 15),
                WorldItem(ItemType.DOCUMENT, 25, 22),
                WorldItem(ItemType.CROSS, 10, 22),
                WorldItem(ItemType.AMMO, 25, 24)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SHADOW, 15, 8, 6),
                MonsterSpawn(MonsterType.WRAITH, 8, 18, 4),
                MonsterSpawn(MonsterType.BOSS, 15, 23, 3)
            ),
            playerSpawn = 20 to 1,
            isDark = true
        )
    }

    // =============== CHURCH (24 x 20) ===============
    private fun buildChurch(): Zone {
        val w = 24; val h = 20
        val t = Array(h) { IntArray(w) { FLOOR } }

        for (y in 0 until h) { t[y][0] = WALL; t[y][w-1] = WALL }
        for (x in 0 until w) { t[0][x] = WALL; t[h-1][x] = WALL }

        // Altar
        for (x in 8..15) { t[3][x] = WALL }
        t[4][11] = DOOR; t[4][12] = DOOR

        // Pews (rows of obstacles)
        for (y in 8..16 step 2) { for (x in 6..17) { t[y][x] = WALL } }
        for (y in 8..16 step 2) { t[y][5] = FLOOR; t[y][18] = FLOOR }

        // Entrance
        t[h-1][11] = DOOR; t[h-1][12] = DOOR

        return Zone(
            id = "church", name = "Церковь",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (h-1 to 11) to ExitTarget("outdoor", 57, 18),
                (h-1 to 12) to ExitTarget("outdoor", 58, 18)
            ),
            items = listOf(
                WorldItem(ItemType.CROSS, 12, 2),
                WorldItem(ItemType.MEDKIT, 4, 10),
                WorldItem(ItemType.DOCUMENT, 12, 16),
                WorldItem(ItemType.AMMO, 20, 5)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SCREAMER, 12, 14, 3),
                MonsterSpawn(MonsterType.SHADOW, 6, 10, 4)
            ),
            playerSpawn = 12 to 17,
            isDark = true
        )
    }

    // =============== MORGUE (20 x 16) ===============
    private fun buildMorgue(): Zone {
        val w = 20; val h = 16
        val t = Array(h) { IntArray(w) { FLOOR } }

        for (y in 0 until h) { t[y][0] = WALL; t[y][w-1] = WALL }
        for (x in 0 until w) { t[0][x] = WALL; t[h-1][x] = WALL }

        // Body storage
        for (y in 2..8) { for (x in 5..14) { t[y][x] = if (x%3==0) WALL else FLOOR } }

        // Autopsy room divider
        for (x in 0..w-1) { t[10][x] = WALL }
        t[10][9] = DOOR; t[10][10] = DOOR

        // Entrance
        t[h-1][9] = DOOR; t[h-1][10] = DOOR

        return Zone(
            id = "morgue", name = "Морг",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (h-1 to 9) to ExitTarget("outdoor", 10, 15),
                (h-1 to 10) to ExitTarget("outdoor", 10, 15)
            ),
            items = listOf(
                WorldItem(ItemType.MEDKIT, 15, 3),
                WorldItem(ItemType.AMMO, 3, 4),
                WorldItem(ItemType.DOCUMENT, 10, 12),
                WorldItem(ItemType.ADRENALINE, 18, 13)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.WRAITH, 10, 5, 3),
                MonsterSpawn(MonsterType.SCREAMER, 15, 12, 3)
            ),
            playerSpawn = 10 to 14,
            isDark = true
        )
    }

    // =============== FOREST (40 x 30) ===============
    private fun buildForest(): Zone {
        val w = 40; val h = 30
        val t = Array(h) { IntArray(w) { GRASS } }

        // Dense trees everywhere
        for (y in 0 until h) { for (x in 0 until w) {
            t[y][x] = when {
                (x in 0..1 || x in w-2..w-1 || y in 0..1 || y in h-2..h-1) -> TREE
                (x*y*7) % 4 == 0 -> TREE
                else -> GRASS
            }
        }}

        // Clearing in center
        for (y in 12..17) { for (x in 17..22) { t[y][x] = GRASS } }

        // Path through forest
        for (x in 3..36) {
            t[8][x] = ROAD; t[22][x] = ROAD
        }
        for (y in 3..26) { t[y][20] = ROAD }

        // Old ruins in forest
        for (y in 3..7) { for (x in 30..36) { t[y][x] = if (y==3||y==7||x==30||x==36) WALL else FLOOR } }
        t[7][33] = DOOR

        return Zone(
            id = "forest", name = "Лес",
            width = w, height = h, tiles = t,
            exits = mapOf(
                (5 to 15) to ExitTarget("outdoor", 10, 70)
            ),
            items = listOf(
                WorldItem(ItemType.MEDKIT, 20, 13),
                WorldItem(ItemType.BATTERY, 33, 5),
                WorldItem(ItemType.AMMO, 10, 22),
                WorldItem(ItemType.GASOLINE, 35, 20),
                WorldItem(ItemType.CROSS, 25, 25)
            ),
            monsterSpawns = listOf(
                MonsterSpawn(MonsterType.SCREAMER, 15, 10, 6),
                MonsterSpawn(MonsterType.SHADOW, 30, 15, 8),
                MonsterSpawn(MonsterType.WRAITH, 20, 25, 7),
                MonsterSpawn(MonsterType.SHADOW, 5, 20, 5)
            ),
            playerSpawn = 5 to 15,
            isDark = true
        )
    }
}
