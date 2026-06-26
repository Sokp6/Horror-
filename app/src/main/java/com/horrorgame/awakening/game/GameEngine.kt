package com.horrorgame.awakening.game

import kotlin.random.Random

/**
 * Main game engine — manages all game state, player actions, and story progression.
 */
class GameEngine {

    private var player = Player()
    private var gameOver = false
    private var ending: StoryData.Ending? = null

    // Callbacks for UI updates
    var onRoomChanged: ((Room) -> Unit)? = null
    var onPlayerStateChanged: ((Player) -> Unit)? = null
    var onMessage: ((String, MessageType) -> Unit)? = null
    var onJumpScare: ((String) -> Unit)? = null
    var onGameOver: ((StoryData.Ending) -> Unit)? = null
    var onItemCollected: ((Item) -> Unit)? = null

    enum class MessageType { NARRATION, SYSTEM, DANGER, ITEM, EVENT, AMBIENT }

    // ==================== INIT ====================

    fun startNewGame() {
        player = Player()
        gameOver = false
        ending = null
        moveToRoom("entrance")
    }

    fun resumeGame(savedPlayer: Player) {
        player = savedPlayer
        gameOver = false
        ending = null
        moveToRoom(player.currentRoomId)
    }

    fun getPlayer(): Player = player

    fun isGameOver(): Boolean = gameOver

    fun getEnding(): StoryData.Ending? = ending

    // ==================== MOVEMENT ====================

    fun move(direction: String) {
        if (gameOver) return

        val currentRoom = StoryData.rooms[player.currentRoomId] ?: return
        val targetRoomId = currentRoom.exits[direction.lowercase()]

        if (targetRoomId == null) {
            emitMessage("Вы не можете идти туда.", MessageType.SYSTEM)
            return
        }

        val targetRoom = StoryData.rooms[targetRoomId]
        if (targetRoom == null) {
            emitMessage("Путь заблокирован.", MessageType.SYSTEM)
            return
        }

        // Check if room is locked
        if (targetRoom.isLocked) {
            val unlockItem = targetRoom.unlockItem
            if (unlockItem != null && player.hasItem(unlockItem)) {
                val item = StoryData.items[unlockItem]
                emitMessage(targetRoom.unlockMessage ?: "Вы открываете дверь.", MessageType.SYSTEM)
                // Mark room as unlocked
                unlockRoom(targetRoomId)
            } else {
                emitMessage("Дверь заперта. Вам нужно что-то, чтобы открыть её.", MessageType.SYSTEM)
                return
            }
        }

        // Check if current room exit is blocked (from unlock)
        val exitKey = "${currentRoom.id}_exit_$direction"
        if (player.getFlag("blocked_$exitKey")) {
            emitMessage("Путь заблокирован.", MessageType.SYSTEM)
            return
        }

        moveToRoom(targetRoomId)

        // Trigger room event if present
        targetRoom.eventTrigger?.let { triggerEvent(it) }

        // Darkness check — lose sanity without light
        if (targetRoom.isDark && !hasLightSource()) {
            player.loseSanity(5)
            emitMessage("Тьма давит на ваш рассудок. (-5 к рассудку)", MessageType.DANGER)
            emitMessage(StoryData.ambientEvents["darkness"]?.random() ?: "", MessageType.AMBIENT)
        }

        // Random ambient event
        if (Random.nextInt(100) < 15) {
            triggerRandomAmbient()
        }

        // Insanity check
        if (player.isInsane) {
            triggerInsanity()
        }

        checkPlayerStatus()
    }

    // ==================== ITEMS ====================

    fun pickUpItem(itemId: String) {
        if (gameOver) return

        val currentRoom = StoryData.rooms[player.currentRoomId] ?: return
        if (!currentRoom.items.contains(itemId)) {
            emitMessage("Этого предмета здесь нет.", MessageType.SYSTEM)
            return
        }

        val item = StoryData.items[itemId] ?: return
        player.addItem(itemId)
        emitMessage("Вы подобрали: ${item.name}", MessageType.ITEM)
        emitMessage(item.description, MessageType.NARRATION)
        onItemCollected?.invoke(item)

        // Special items trigger sanity effects
        if (item.id == "patient_history") {
            player.loseSanity(10)
            emitMessage("Вы узнаёте себя на фотографии... (-10 к рассудку)", MessageType.DANGER)
        }
        if (item.id == "photograph") {
            player.loseSanity(5)
            emitMessage("Вы узнаёте себя среди пациентов... (-5 к рассудку)", MessageType.DANGER)
        }
        if (item.id == "secret_document") {
            player.restoreSanity(10)
            emitMessage("Правда приносит странное облегчение. (+10 к рассудку)", MessageType.EVENT)
        }
    }

    fun useItem(itemId: String) {
        if (gameOver) return

        if (!player.hasItem(itemId)) {
            emitMessage("У вас нет этого предмета.", MessageType.SYSTEM)
            return
        }

        val item = StoryData.items[itemId] ?: return

        if (!item.isUsable) {
            emitMessage("Вы не можете использовать ${item.name} здесь.", MessageType.SYSTEM)
            return
        }

        // Use on specific room
        if (item.useOnRoom != null) {
            if (player.currentRoomId == item.useOnRoom) {
                // Correct room
                emitMessage(item.useResult ?: "Вы используете ${item.name}.", MessageType.EVENT)

                // Handle effects
                item.useOpensExit?.let { exit ->
                    val key = "blocked_${item.useOnRoom}_exit_$exit"
                    player.setFlag(key, false)
                }
                item.useGrantsItem?.let { player.addItem(it) }

                // Consume item
                if (item.consumeOnUse) {
                    player.removeItem(itemId)
                    emitMessage("${item.name} израсходован.", MessageType.SYSTEM)
                }

                // Apply stat changes
                if (item.healthRestore > 0) {
                    player.heal(item.healthRestore)
                    emitMessage("+${item.healthRestore} к здоровью.", MessageType.SYSTEM)
                }
                if (item.sanityRestore > 0) {
                    player.restoreSanity(item.sanityRestore)
                    emitMessage("+${item.sanityRestore} к рассудку.", MessageType.SYSTEM)
                }
            } else {
                emitMessage("Вы не можете использовать ${item.name} здесь. Попробуйте в другом месте.", MessageType.SYSTEM)
            }
        } else {
            // Usable anywhere
            if (item.healthRestore > 0) {
                player.heal(item.healthRestore)
                emitMessage("+${item.healthRestore} к здоровью.", MessageType.SYSTEM)
            }
            if (item.sanityRestore > 0) {
                player.restoreSanity(item.sanityRestore)
                emitMessage("+${item.sanityRestore} к рассудку.", MessageType.SYSTEM)
            }
            if (item.consumeOnUse) {
                player.removeItem(itemId)
                emitMessage("${item.name} израсходован.", MessageType.SYSTEM)
            }
        }

        onPlayerStateChanged?.invoke(player)
        checkPlayerStatus()
    }

    fun examineItem(itemId: String): String {
        val item = StoryData.items[itemId] ?: return "Неизвестный предмет."
        return item.examineText
    }

    // ==================== COMBAT / FINAL ENCOUNTER ====================

    fun shoot() {
        if (gameOver) return
        if (!player.hasItem("revolver")) {
            emitMessage("У вас нет оружия.", MessageType.SYSTEM)
            return
        }
        if (player.currentRoomId != "basement") {
            emitMessage("Вы стреляете в воздух. Эхо разносится по коридорам.", MessageType.EVENT)
            player.loseSanity(5)
            return
        }

        // Final confrontation in basement
        if (player.hasItem("amulet") && player.hasItem("spell_page")) {
            // Player has both protection items — shoot and use spell
            emitMessage("Вы стреляете в стальную дверь. Пуля пробивает её насквозь!", MessageType.EVENT)
            emitMessage("Из-за двери раздаётся НЕЧЕЛОВЕЧЕСКИЙ ВОПЛЬ.", MessageType.DANGER)
            emitMessage("Пока существо дезориентировано, вы используете страницу заклинания...", MessageType.EVENT)
            triggerEnding(StoryData.Ending.Good)
        } else if (player.hasItem("doctor_diary") && player.hasItem("patient_history") && player.hasItem("medical_record")) {
            // Player knows the truth — secret ending
            emitMessage("Вы подходите к двери. Дрожащей рукой вы открываете её.", MessageType.EVENT)
            emitMessage("Вместо выстрела вы говорите: 'Прости меня.'", MessageType.NARRATION)
            triggerEnding(StoryData.Ending.Secret)
        } else {
            // Unprepared — bad ending
            emitMessage("Вы стреляете в дверь. Пуля отскакивает от стали.", MessageType.EVENT)
            emitMessage("Существо СМЕЁТСЯ. 'Этого недостаточно,' — говорит оно вашим голосом.", MessageType.DANGER)
            emitMessage("Дверь распахивается. Тьма поглощает вас.", MessageType.DANGER)
            triggerEnding(StoryData.Ending.Bad)
        }
    }

    fun examineFinalDoor() {
        if (player.currentRoomId != "basement") return

        emitMessage("Вы заглядываете в окошко стальной двери...", MessageType.NARRATION)
        emitMessage("В кромешной тьме что-то движется. ДВА БЕЛЫХ ГЛАЗА смотрят прямо на вас.", MessageType.DANGER)
        onJumpScare?.invoke("ДВА ГЛАЗА СМОТРЯТ НА ВАС В УПОР!")

        player.loseSanity(20)
        emitMessage("Ваш рассудок трещит по швам! (-20 к рассудку)", MessageType.DANGER)
        onPlayerStateChanged?.invoke(player)
        checkPlayerStatus()
    }

    // ==================== EVENTS ====================

    private fun triggerEvent(eventId: String) {
        when (eventId) {
            "monitor_flicker" -> {
                emitMessage("Монитор внезапно включается! На экране — помехи, но в них проступает СИЛУЭТ.", MessageType.EVENT)
                onJumpScare?.invoke("СИЛУЭТ НА ЭКРАНЕ!")
                player.loseSanity(5)
            }
            "shadow_figure" -> {
                emitMessage("Тень в углу комнаты отделяется от стены и движется к вам!", MessageType.DANGER)
                onJumpScare?.invoke("ТЕНЬ ДВИЖЕТСЯ!")
                player.loseSanity(10)
                player.takeDamage(5)
                emitMessage("Что-то царапает вас! (-5 здоровья, -10 рассудка)", MessageType.DANGER)
            }
            "figure_breathing" -> {
                emitMessage("Фигура на койке... ШЕВЕЛИТСЯ.", MessageType.DANGER)
                emitMessage("Она медленно поворачивает голову в вашу сторону.", MessageType.DANGER)
                onJumpScare?.invoke("ФИГУРА НА КОЙКЕ ДВИЖЕТСЯ!")
                player.loseSanity(15)
            }
            "stairs_creak" -> {
                emitMessage("Ступени за вашей спиной скрипят. Кто-то поднимается следом.", MessageType.DANGER)
                player.loseSanity(5)
            }
            "door_slam" -> {
                emitMessage("Дверь за вашей спиной захлопывается с грохотом!", MessageType.DANGER)
                onJumpScare?.invoke("ДВЕРЬ ЗАХЛОПНУЛАСЬ!")
                player.loseSanity(5)
            }
            "mirror_horror" -> {
                emitMessage("Вы смотрите в зеркало. Ваше отражение улыбается — но вы не улыбаетесь.", MessageType.DANGER)
                onJumpScare?.invoke("ОТРАЖЕНИЕ УЛЫБАЕТСЯ!")
                player.loseSanity(15)
            }
            "basement_voice" -> {
                emitMessage("ГОЛОС ИЗ-ЗА ДВЕРИ произносит ваше имя. Ваше НАСТОЯЩЕЕ имя.", MessageType.DANGER)
                emitMessage("'Вернись ко мне,' — шепчет голос. — 'Мы одно целое.'", MessageType.NARRATION)
                player.loseSanity(10)
            }
        }
        onPlayerStateChanged?.invoke(player)
        checkPlayerStatus()
    }

    private fun triggerRandomAmbient() {
        val currentRoom = StoryData.rooms[player.currentRoomId] ?: return
        val ambientText = currentRoom.ambientText

        if (ambientText != null) {
            emitMessage(ambientText, MessageType.AMBIENT)
        }

        // Random danger event
        if (player.sanity < 50 && Random.nextInt(100) < 20) {
            val dangerMessages = StoryData.ambientEvents["danger"] ?: return
            emitMessage(dangerMessages.random(), MessageType.DANGER)
            player.loseSanity(3)
        }

        if (player.sanity < 30 && Random.nextInt(100) < 25) {
            val insanityMessages = StoryData.ambientEvents["insanity"] ?: return
            emitMessage(insanityMessages.random(), MessageType.DANGER)
            player.loseSanity(5)
        }

        // Random jump scare (low probability)
        if (player.sanity < 40 && Random.nextInt(100) < 8) {
            onJumpScare?.invoke(StoryData.jumpScares.random())
            player.loseSanity(8)
            emitMessage("Ваше сердце колотится. (-8 к рассудку)", MessageType.DANGER)
        }

        onPlayerStateChanged?.invoke(player)
        checkPlayerStatus()
    }

    private fun triggerInsanity() {
        emitMessage(StoryData.ambientEvents["insanity"]?.random() ?: "", MessageType.DANGER)
        onPlayerStateChanged?.invoke(player)

        if (player.sanity <= -20) {
            triggerEnding(StoryData.Ending.Insanity)
        }
    }

    // ==================== INTERNAL ====================

    private fun moveToRoom(roomId: String) {
        val room = StoryData.rooms[roomId]
        if (room == null) {
            emitMessage("Ошибка: комната не найдена.", MessageType.SYSTEM)
            return
        }

        player = player.copy(currentRoomId = roomId)
        val isFirstVisit = player.visitedRooms.add(roomId)
        player.advanceTime(5)

        if (isFirstVisit) {
            emitMessage("=== ${room.name} ===", MessageType.SYSTEM)
            emitMessage(room.detailedDescription, MessageType.NARRATION)

            // List visible items on first visit
            if (room.items.isNotEmpty()) {
                val itemNames = room.items.mapNotNull { StoryData.items[it]?.name }
                if (itemNames.isNotEmpty()) {
                    emitMessage("Вы замечаете: ${itemNames.joinToString(", ")}", MessageType.ITEM)
                }
            }
        } else {
            emitMessage("=== ${room.name} ===", MessageType.SYSTEM)
            emitMessage(room.description, MessageType.NARRATION)
        }

        // List exits
        val exitDirections = room.exits.keys.toList()
        if (exitDirections.isNotEmpty()) {
            emitMessage("Выходы: ${exitDirections.joinToString(", ").uppercase()}", MessageType.SYSTEM)
        }

        onRoomChanged?.invoke(room)
        onPlayerStateChanged?.invoke(player)
    }

    private fun unlockRoom(roomId: String) {
        // We need to mutate the room's isLocked, but since Room is a data class...
        // We store unlocked state in player flags
        player.setFlag("unlocked_$roomId", true)
    }

    private fun hasLightSource(): Boolean {
        return player.hasItem("flashlight") || player.hasItem("lighter") || player.hasItem("amulet")
    }

    private fun checkPlayerStatus() {
        if (!player.isAlive && !gameOver) {
            if (player.isDead) {
                emitMessage("Ваше тело больше не может держаться...", MessageType.DANGER)
                triggerEnding(StoryData.Ending.Bad)
            } else if (player.isInsane && player.sanity <= -20) {
                triggerEnding(StoryData.Ending.Insanity)
            }
        }
    }

    private fun triggerEnding(end: StoryData.Ending) {
        gameOver = true
        ending = end
        emitMessage(end.title, MessageType.SYSTEM)
        emitMessage(end.text, MessageType.NARRATION)
        onGameOver?.invoke(end)
    }

    private fun emitMessage(text: String, type: MessageType) {
        onMessage?.invoke(text, type)
    }

    // ==================== SAVE / LOAD ====================

    fun getSaveData(): Player = player
}
