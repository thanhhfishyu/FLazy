package io.github.thanhhfishyu.flazy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The scanner. Runs as an accessibility service only so it can (1) take a screenshot of the
 * page you are looking at and (2) tap or swipe to turn the page. It shows a small floating
 * button; nothing happens until you press ▶ on it.
 */
class ScanService : AccessibilityService() {

    private class StopScan(message: String) : Exception(message)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanJob: Job? = null
    private lateinit var windowManager: WindowManager
    private lateinit var ocr: OcrEngine

    private var overlay: LinearLayout? = null
    private var playButton: TextView? = null
    private var statusText: TextView? = null
    private var lastBookName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        ocr = OcrEngine()
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scanJob?.cancel()
        removeOverlay()
        if (::ocr.isInitialized) ocr.close()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- floating button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showOverlay() {
        if (overlay != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(96)
        }

        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            setPadding(dp(10), dp(8), dp(6), dp(8))
        }
        val play = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { if (scanJob?.isActive == true) stopScan() else startScan() }
        }
        val status = TextView(this).apply {
            text = "FLazy"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(8), dp(14), dp(8))
            setOnClickListener { openApp() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.overlay_bg)
            addView(handle)
            addView(play)
            addView(status)
        }

        // Drag the bubble by its handle so it never covers the spot that turns the page.
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params)
                }
            }
            true
        }

        windowManager.addView(root, params)
        overlay = root
        playButton = play
        statusText = status
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun setStatus(text: String) {
        statusText?.text = text
    }

    private fun openApp() {
        if (scanJob?.isActive == true) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    // ---------------------------------------------------------------- scanning

    private fun startScan() {
        val prefs = Prefs(this)
        val book = BookStore.create(this, prefs.bookTitle)
        lastBookName = book.file.name
        playButton?.text = "■"
        setStatus("Bắt đầu…")

        scanJob = scope.launch {
            var pages = 0
            var reason = "Đã đủ ${prefs.maxPages} trang (giới hạn trong cài đặt)"
            try {
                var previousLines = emptyList<OcrLine>()
                var previousNorm = ""
                var samePageCount = 0
                var emptyCount = 0
                delay(600)

                while (isActive && pages < prefs.maxPages) {
                    val lines = readScreen(prefs)
                    val norm = PageText.normalize(lines)

                    if (norm.isEmpty()) {
                        emptyCount++
                        if (emptyCount >= 3) throw StopScan(
                            "3 trang liền không đọc được chữ. App đọc sách có thể chặn chụp màn hình, " +
                                "hoặc chữ quá nhỏ/bị cắt mất."
                        )
                        pages++
                        book.appendPage(pages, "[Không đọc được chữ trên trang này]")
                    } else {
                        emptyCount = 0
                        if (PageText.similarity(norm, previousNorm) >= 0.95) {
                            // The page did not change: either the turn missed or the book ended.
                            samePageCount++
                            if (samePageCount >= 2) throw StopScan("Trang không đổi nữa, có thể đã hết sách.")
                        } else {
                            samePageCount = 0
                            val fresh = if (prefs.turnMode == TurnMode.SCROLL_UP)
                                PageText.dropOverlap(previousLines, lines) else lines
                            pages++
                            withContext(Dispatchers.IO) { book.appendPage(pages, PageText.format(fresh)) }
                            previousLines = lines
                            previousNorm = norm
                        }
                    }
                    setStatus("Trang $pages")
                    turnPage(prefs.turnMode)
                    delay(prefs.delayMs)
                }
            } catch (e: StopScan) {
                reason = e.message ?: reason
            } catch (e: CancellationException) {
                reason = "Bạn đã dừng."
                throw e
            } catch (e: Exception) {
                reason = "Lỗi: ${e.message}"
            } finally {
                withContext(NonCancellable) { finishScan(book, pages, reason) }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
    }

    private suspend fun finishScan(book: BookStore.Book, pages: Int, reason: String) {
        withContext(Dispatchers.IO) {
            book.appendNote("Kết thúc sau $pages trang. $reason")
            BookStore.exportToDownloads(this@ScanService, book.file)
        }
        playButton?.text = "▶"
        setStatus("Xong $pages trang · chạm để mở")
        Toast.makeText(
            this,
            "FLazy: đã lưu $pages trang. $reason\nMở FLazy để gửi file lên Google Drive.",
            Toast.LENGTH_LONG
        ).show()
    }

    /** Screenshot (with the bubble hidden), crop header/footer, then OCR. */
    private suspend fun readScreen(prefs: Prefs): List<OcrLine> {
        val full = captureWithoutOverlay()
        val top = full.height * prefs.cropTopPct / 100
        val bottom = full.height * prefs.cropBottomPct / 100
        val height = (full.height - top - bottom).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(full, 0, top, full.width, height)
        return try {
            ocr.read(cropped)
        } finally {
            if (cropped !== full) cropped.recycle()
            full.recycle()
        }
    }

    private suspend fun captureWithoutOverlay(): Bitmap {
        overlay?.visibility = View.INVISIBLE
        delay(150)
        try {
            return capture()
        } finally {
            overlay?.visibility = View.VISIBLE
        }
    }

    private suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                hardware?.recycle()
                buffer.close()
                if (bitmap != null) cont.resume(bitmap)
                else cont.resumeWithException(StopScan("Không chụp được màn hình."))
            }

            override fun onFailure(errorCode: Int) {
                val message = when (errorCode) {
                    ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                        "App đọc sách này chặn chụp màn hình, FLazy không thể quét nó."
                    ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                        "Chụp quá nhanh, hãy tăng thời gian chờ giữa các trang."
                    else -> "Không chụp được màn hình (mã lỗi $errorCode)."
                }
                cont.resumeWithException(StopScan(message))
            }
        })
    }

    /** Taps or swipes like a finger would, to go to the next page. */
    private suspend fun turnPage(mode: TurnMode): Boolean {
        val bounds = windowManager.currentWindowMetrics.bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val path = Path()
        val duration: Long
        when (mode) {
            TurnMode.TAP_RIGHT -> { path.moveTo(w * 0.9f, h * 0.55f); duration = 60 }
            TurnMode.TAP_LEFT -> { path.moveTo(w * 0.1f, h * 0.55f); duration = 60 }
            TurnMode.SWIPE_LEFT -> {
                path.moveTo(w * 0.85f, h * 0.55f); path.lineTo(w * 0.15f, h * 0.55f); duration = 300
            }
            TurnMode.SWIPE_RIGHT -> {
                path.moveTo(w * 0.15f, h * 0.55f); path.lineTo(w * 0.85f, h * 0.55f); duration = 300
            }
            TurnMode.SCROLL_UP -> {
                // Slow drag (no fling) of about half a screen; overlap is removed later.
                path.moveTo(w * 0.5f, h * 0.78f); path.lineTo(w * 0.5f, h * 0.30f); duration = 700
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return suspendCancellableCoroutine { cont ->
            val sent = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!sent && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        @Volatile
        var instance: ScanService? = null
            private set
    }
}
