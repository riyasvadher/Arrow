package com.example.arrows

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.arrows.SoundManager.Sfx
import com.example.arrows.data.DatabaseHelper
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL = "level"
        private const val MAX_LIVES = 3
    }

    private lateinit var db: DatabaseHelper
    private lateinit var sound: SoundManager
    private lateinit var board: ArrowBoardView
    private lateinit var tvTitle: TextView
    private lateinit var tvHearts: TextView
    private lateinit var progress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null

    private var level = 1
    private var lives = MAX_LIVES
    private var mistakes = 0
    private var startMs = 0L
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        db = DatabaseHelper.get(this)
        sound = SoundManager(this).also { it.enabled = db.isSoundOn() }
        level = intent.getIntExtra(EXTRA_LEVEL, 1)

        board = findViewById(R.id.board)
        tvTitle = findViewById(R.id.tvTitle)
        tvHearts = findViewById(R.id.tvHearts)
        progress = findViewById(R.id.progress)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnRestart).setOnClickListener { startLevel() }
        findViewById<ImageButton>(R.id.btnHint).setOnClickListener { board.showHint() }
        findViewById<ImageButton>(R.id.btnFit).setOnClickListener { board.toggleZoom() }

        board.onSlide = { sound.play(Sfx.SLIDE, 0.9f, 0.9f + Random.nextFloat() * 0.3f) }
        board.onBlocked = {
            sound.play(Sfx.COLLISION)
            mistakes++
            lives--
            updateHearts()
            if (lives <= 0 && !finished) onGameOver()
        }
        board.onProgress = { remaining ->
            val total = board.totalArrows.coerceAtLeast(1)
            progress.setProgress((total - remaining) * 100 / total, true)
            if (remaining == 0 && !finished) onWin()
        }

        startLevel()
    }

    override fun onResume() {
        super.onResume()
        sound.enabled = db.isSoundOn()
    }

    private fun startLevel() {
        dialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
        finished = false
        lives = MAX_LIVES
        mistakes = 0
        board.inputEnabled = true
        tvTitle.text = "Level $level · ${LevelConfig.difficulty(level)}"
        progress.progress = 0
        updateHearts()
        board.loadLevel(level)
        startMs = SystemClock.elapsedRealtime()
    }

    private fun updateHearts() {
        val sb = SpannableStringBuilder()
        for (i in 0 until MAX_LIVES) {
            val start = sb.length
            sb.append("♥")
            if (i < MAX_LIVES - 1) sb.append(" ")
            sb.setSpan(
                ForegroundColorSpan(getColor(if (i < lives) R.color.heart else R.color.heart_lost)),
                start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvHearts.text = sb
    }

    // ------------------------------------------------------------------ end of game

    private fun onWin() {
        finished = true
        board.inputEnabled = false
        val elapsed = SystemClock.elapsedRealtime() - startMs
        val stars = lives.coerceIn(1, 3)               // 3 hearts left = 3 stars
        val firstWinToday = db.winsToday() == 0
        db.saveResult(level, true, stars, elapsed, mistakes)
        val extra = buildExtra(firstWinToday)
        sound.play(Sfx.WIN)
        // zoom out, let the silhouette fill in, then show the pop-up
        board.playReveal { handler.postDelayed({ showResult(true, stars, elapsed, extra) }, 700) }
    }

    private fun onGameOver() {
        finished = true
        board.inputEnabled = false
        val elapsed = SystemClock.elapsedRealtime() - startMs
        db.saveResult(level, false, 0, elapsed, mistakes)
        val extra = buildExtra(false)
        handler.postDelayed({ sound.play(Sfx.GAME_OVER) }, 400)
        handler.postDelayed({ showResult(false, 0, elapsed, extra) }, 900)
    }

    /** Extra lines for the pop-up: daily streak progress + newly unlocked achievements. */
    private fun buildExtra(checkStreak: Boolean): String {
        val lines = ArrayList<String>()
        if (checkStreak) {
            val streak = StreakCalculator.compute(db.activeDays()).daily.current
            if (streak >= 2) lines.add("🔥 $streak-day streak!")
        }
        Achievements.checkNewlyUnlocked(db).forEach { lines.add("🏆 Achievement unlocked: ${it.title}") }
        return lines.joinToString("\n")
    }

    private fun showResult(won: Boolean, stars: Int, elapsedMs: Long, extra: String) {
        if (isFinishing || isDestroyed) return
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_result)
        d.setCancelable(false)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.45f)
        }

        val title = d.findViewById<TextView>(R.id.tvResultTitle)
        val sub = d.findViewById<TextView>(R.id.tvResultSub)
        val btnPrimary = d.findViewById<Button>(R.id.btnPrimary)
        val btnSecondary = d.findViewById<Button>(R.id.btnSecondary)
        val btnMenu = d.findViewById<Button>(R.id.btnMenu)
        val card = d.findViewById<View>(R.id.card)
        val starViews = listOf<TextView>(d.findViewById(R.id.star1), d.findViewById(R.id.star2), d.findViewById(R.id.star3))

        val secs = elapsedMs / 1000
        val time = "%d:%02d".format(secs / 60, secs % 60)
        d.findViewById<TextView>(R.id.tvExtra).apply {
            if (extra.isNotEmpty()) { text = extra; visibility = View.VISIBLE }
        }

        if (won) {
            title.text = "Level $level cleared!"
            sub.text = "${board.shapeName}  ·  $time  ·  $mistakes mistake${if (mistakes == 1) "" else "s"}"
            val last = level >= DatabaseHelper.TOTAL_LEVELS
            btnPrimary.text = if (last) "Finish" else "Next level"
            btnPrimary.setOnClickListener {
                if (last) { d.dismiss(); finish() } else { level++; startLevel() }
            }
            btnSecondary.text = "Play again"
            btnSecondary.setOnClickListener { startLevel() }
        } else {
            title.text = "Out of hearts"
            sub.text = "Level $level  ·  $time"
            btnPrimary.text = "Try again"
            btnPrimary.setOnClickListener { startLevel() }
            btnSecondary.visibility = View.GONE
        }
        btnMenu.setOnClickListener { d.dismiss(); finish() }

        // pop-in animation: card first, then the earned stars one after another
        card.scaleX = 0.85f; card.scaleY = 0.85f; card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
        starViews.forEachIndexed { i, tv ->
            val earned = i < stars
            tv.setTextColor(getColor(if (earned) R.color.star else R.color.star_empty))
            if (earned) {
                tv.scaleX = 0f; tv.scaleY = 0f
                tv.animate().scaleX(1f).scaleY(1f).setStartDelay(300L + i * 220L).setDuration(350)
                    .setInterpolator(OvershootInterpolator(2.5f)).start()
            }
        }

        dialog = d
        d.show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        dialog?.dismiss()
        sound.release()
        super.onDestroy()
    }
}
