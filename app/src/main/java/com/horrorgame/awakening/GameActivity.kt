package com.horrorgame.awakening

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.horrorgame.awakening.game.*
import java.util.Random

class GameActivity : AppCompatActivity() {

    private lateinit var engine: GameEngine
    private lateinit var soundManager: SoundManager
    private lateinit var saveManager: SaveManager
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    // UI components
    private lateinit var tvRoomName: TextView
    private lateinit var tvStoryText: TextView
    private lateinit var tvHealthBar: ProgressBar
    private lateinit var tvSanityBar: ProgressBar
    private lateinit var tvHealthText: TextView
    private lateinit var tvSanityText: TextView
    private lateinit var rvInventory: RecyclerView
    private lateinit var layoutExits: LinearLayout
    private lateinit var layoutActions: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var screenOverlay: View
    private lateinit var tvJumpScare: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        engine = GameEngine()
        soundManager = SoundManager(this)
        saveManager = SaveManager(this)

        bindViews()
        setupCallbacks()
        setupInventory()

        val newGame = intent.getBooleanExtra("new_game", true)
        if (newGame) {
            showIntro()
        } else {
            val saved = saveManager.loadGame()
            if (saved != null) {
                engine.resumeGame(saved)
            } else {
                showIntro()
            }
        }
    }

    private fun bindViews() {
        tvRoomName = findViewById(R.id.tv_room_name)
        tvStoryText = findViewById(R.id.tv_story_text)
        tvHealthBar = findViewById(R.id.progress_health)
        tvSanityBar = findViewById(R.id.progress_sanity)
        tvHealthText = findViewById(R.id.tv_health_text)
        tvSanityText = findViewById(R.id.tv_sanity_text)
        rvInventory = findViewById(R.id.rv_inventory)
        layoutExits = findViewById(R.id.layout_exits)
        layoutActions = findViewById(R.id.layout_actions)
        scrollView = findViewById(R.id.scroll_story)
        screenOverlay = findViewById(R.id.screen_overlay)
        tvJumpScare = findViewById(R.id.tv_jumpscare)
    }

    private fun setupCallbacks() {
        engine.onRoomChanged = { room ->
            tvRoomName.text = room.name
            updateExits(room)
            updateStatusBars()
        }

        engine.onPlayerStateChanged = { player ->
            updateStatusBars()
        }

        engine.onMessage = { text, type ->
            appendStoryText(text, type)
        }

        engine.onJumpScare = { text ->
            triggerJumpScare(text)
        }

        engine.onGameOver = { ending ->
            showGameOver(ending)
        }

        engine.onItemCollected = { _ ->
            updateInventory()
        }
    }

    // ==================== INTRO ====================

    private fun showIntro() {
        val builder = AlertDialog.Builder(this, R.style.HorrorDialog)
        builder.setTitle("ПРОБУЖДЕНИЕ")
        builder.setMessage(StoryData.introText)
        builder.setCancelable(false)
        builder.setPositiveButton("НАЧАТЬ ИГРУ") { dialog, _ ->
            dialog.dismiss()
            engine.startNewGame()
        }
        builder.show()
    }

    // ==================== STORY TEXT ====================

    private fun appendStoryText(text: String, type: GameEngine.MessageType) {
        val color = when (type) {
            GameEngine.MessageType.NARRATION -> "#D4C5C4"
            GameEngine.MessageType.SYSTEM -> "#9E9E9E"
            GameEngine.MessageType.DANGER -> "#E53935"
            GameEngine.MessageType.ITEM -> "#4CAF50"
            GameEngine.MessageType.EVENT -> "#FFC107"
            GameEngine.MessageType.AMBIENT -> "#757575"
        }

        val currentText = tvStoryText.text
        val newLine = if (currentText.isNotEmpty()) "\n\n" else ""
        tvStoryText.text = "${currentText}${newLine}${text}"

        // Scroll to bottom
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }

        // Animate text color for danger messages
        if (type == GameEngine.MessageType.DANGER) {
            tvStoryText.setTextColor(android.graphics.Color.parseColor("#E53935"))
            handler.postDelayed({
                tvStoryText.setTextColor(android.graphics.Color.parseColor("#D4C5C4"))
            }, 800)
        }
    }

    // ==================== EXITS ====================

    private fun updateExits(room: Room) {
        layoutExits.removeAllViews()

        room.exits.forEach { (direction, _) ->
            val btn = Button(this).apply {
                text = direction.uppercase()
                setTextColor(Color.parseColor("#D4C5C4"))
                setBackgroundColor(Color.parseColor("#2D2D2D"))
                textSize = 14f
                isAllCaps = true
                setOnClickListener {
                    engine.move(direction)
                }
            }
            layoutExits.addView(btn)
        }

        // Special button for basement
        if (room.id == "basement") {
            val btnExamine = Button(this).apply {
                text = "ЗАГЛЯНУТЬ В ДВЕРЬ"
                setTextColor(Color.parseColor("#E53935"))
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                textSize = 14f
                isAllCaps = true
                setOnClickListener {
                    engine.examineFinalDoor()
                }
            }
            layoutActions.removeAllViews()
            layoutActions.addView(btnExamine)
        }
    }

    // ==================== INVENTORY ====================

    private fun setupInventory() {
        rvInventory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        updateInventory()
    }

    private fun updateInventory() {
        val items = engine.getPlayer().inventory.mapNotNull { StoryData.items[it] }
        rvInventory.adapter = InventoryAdapter(items) { item ->
            showItemDialog(item)
        }
    }

    private fun showItemDialog(item: Item) {
        val builder = AlertDialog.Builder(this, R.style.HorrorDialog)
        builder.setTitle(item.name)
        builder.setMessage("${item.description}\n\nВыберите действие:")

        val actions = mutableListOf<String>()
        actions.add("Осмотреть")
        if (item.isUsable) actions.add("Использовать")
        if (item.healthRestore > 0 || item.sanityRestore > 0) actions.add("Применить на себя")

        builder.setItems(actions.toTypedArray()) { _, which ->
            when (actions[which]) {
                "Осмотреть" -> {
                    val text = engine.examineItem(item.id)
                    appendStoryText(text, GameEngine.MessageType.NARRATION)
                }
                "Использовать" -> {
                    engine.useItem(item.id)
                    updateInventory()
                }
                "Применить на себя" -> {
                    engine.useItem(item.id)
                    updateInventory()
                }
            }
        }
        builder.setNegativeButton("Закрыть", null)
        builder.show()
    }

    // ==================== STATUS BARS ====================

    private fun updateStatusBars() {
        val player = engine.getPlayer()
        tvHealthBar.progress = player.health
        tvHealthText.text = "${player.health}%"
        tvSanityBar.progress = player.sanity
        tvSanityText.text = "${player.sanity}%"

        // Color transitions
        val healthColor = when {
            player.health > 70 -> Color.parseColor("#4CAF50")
            player.health > 30 -> Color.parseColor("#FFC107")
            else -> Color.parseColor("#E53935")
        }
        tvHealthBar.progressTintList = android.content.res.ColorStateList.valueOf(healthColor)

        val sanityColor = when {
            player.sanity > 70 -> Color.parseColor("#4CAF50")
            player.sanity > 30 -> Color.parseColor("#FFC107")
            else -> Color.parseColor("#E53935")
        }
        tvSanityBar.progressTintList = android.content.res.ColorStateList.valueOf(sanityColor)
    }

    // ==================== JUMP SCARES ====================

    private fun triggerJumpScare(text: String) {
        soundManager.vibrateJumpScare()

        // Show jump scare overlay
        tvJumpScare.text = text
        tvJumpScare.visibility = View.VISIBLE
        screenOverlay.visibility = View.VISIBLE

        // Flash effect
        val flashAnim = ObjectAnimator.ofObject(
            screenOverlay, "backgroundColor",
            ArgbEvaluator(),
            Color.TRANSPARENT,
            Color.parseColor("#CC000000"),
            Color.parseColor("#80E53935"),
            Color.parseColor("#CC000000")
        )
        flashAnim.duration = 800
        flashAnim.start()

        // Shake effect on the root view
        val rootView = findViewById<View>(android.R.id.content)
        val shakeAnim = android.view.animation.TranslateAnimation(0f, 10f, 0f, -10f).apply {
            duration = 50
            repeatCount = 5
            repeatMode = Animation.REVERSE
        }
        rootView.startAnimation(shakeAnim)

        // Hide after delay
        handler.postDelayed({
            tvJumpScare.visibility = View.GONE
            screenOverlay.visibility = View.GONE
        }, 1200)
    }

    // ==================== GAME OVER ====================

    private fun showGameOver(ending: StoryData.Ending) {
        soundManager.vibratePulse()
        handler.postDelayed({
            soundManager.stopVibration()
        }, 2000)

        handler.postDelayed({
            val builder = AlertDialog.Builder(this, R.style.HorrorDialog)
            builder.setTitle(ending.title)
            builder.setMessage(ending.text)
            builder.setCancelable(false)
            builder.setPositiveButton("НОВАЯ ИГРА") { _, _ ->
                engine.startNewGame()
                tvStoryText.text = ""
                updateInventory()
            }
            builder.setNegativeButton("В МЕНЮ") { _, _ ->
                finish()
            }
            builder.show()
        }, 500)
    }

    // ==================== LIFECYCLE ====================

    override fun onPause() {
        super.onPause()
        soundManager.stopVibration()
        saveManager.saveGame(engine.getPlayer())
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}

// ==================== INVENTORY ADAPTER ====================

class InventoryAdapter(
    private val items: List<Item>,
    private val onClick: (Item) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val name: TextView = view.findViewById(R.id.tv_item_name)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name

        val color = when {
            item.isKey -> android.graphics.Color.parseColor("#FFC107")   // Gold for key items
            item.healthRestore > 0 -> android.graphics.Color.parseColor("#4CAF50")  // Green for health
            item.sanityRestore > 0 -> android.graphics.Color.parseColor("#2196F3")  // Blue for sanity
            else -> android.graphics.Color.parseColor("#D4C5C4")
        }
        holder.name.setTextColor(color)

        holder.card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
