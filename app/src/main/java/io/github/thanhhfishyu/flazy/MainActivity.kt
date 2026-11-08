package io.github.thanhhfishyu.flazy

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var serviceStatus: TextView
    private lateinit var bookTitle: TextInputEditText
    private lateinit var turnMode: RadioGroup
    private lateinit var delay: TextInputEditText
    private lateinit var maxPages: TextInputEditText
    private lateinit var cropTop: TextInputEditText
    private lateinit var cropBottom: TextInputEditText
    private lateinit var filesContainer: LinearLayout
    private lateinit var emptyFiles: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        serviceStatus = findViewById(R.id.serviceStatus)
        bookTitle = findViewById(R.id.bookTitle)
        turnMode = findViewById(R.id.turnMode)
        delay = findViewById(R.id.delay)
        maxPages = findViewById(R.id.maxPages)
        cropTop = findViewById(R.id.cropTop)
        cropBottom = findViewById(R.id.cropBottom)
        filesContainer = findViewById(R.id.filesContainer)
        emptyFiles = findViewById(R.id.emptyFiles)

        TurnMode.entries.forEachIndexed { i, mode ->
            turnMode.addView(RadioButton(this).apply {
                id = 1000 + i
                text = mode.label
            })
        }

        findViewById<MaterialButton>(R.id.openAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.shareAll).setOnClickListener { shareAll() }

        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        refreshFiles()
    }

    override fun onPause() {
        saveSettings()
        super.onPause()
    }

    // ---------------------------------------------------------------- settings

    private fun loadSettings() {
        bookTitle.setText(prefs.bookTitle)
        turnMode.check(1000 + prefs.turnMode.ordinal)
        delay.setText(String.format(Locale.US, "%.1f", prefs.delayMs / 1000.0))
        maxPages.setText(prefs.maxPages.toString())
        cropTop.setText(prefs.cropTopPct.toString())
        cropBottom.setText(prefs.cropBottomPct.toString())
    }

    private fun saveSettings() {
        prefs.bookTitle = bookTitle.text?.toString().orEmpty()
        val checked = turnMode.checkedRadioButtonId - 1000
        TurnMode.entries.getOrNull(checked)?.let { prefs.turnMode = it }
        delay.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            ?.let { prefs.delayMs = (it * 1000).toLong() }
        maxPages.text?.toString()?.toIntOrNull()?.let { prefs.maxPages = it }
        cropTop.text?.toString()?.toIntOrNull()?.let { prefs.cropTopPct = it }
        cropBottom.text?.toString()?.toIntOrNull()?.let { prefs.cropBottomPct = it }
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == ScanService::class.java.name
            }
    }

    private fun updateServiceStatus() {
        serviceStatus.text = if (isServiceEnabled())
            "✅ Đã bật. Nút nổi FLazy đang hiện trên màn hình."
        else
            "⚠️ Chưa bật. Bấm nút bên dưới để bật."
    }

    // ---------------------------------------------------------------- files

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.files", file)

    private fun refreshFiles() {
        filesContainer.removeAllViews()
        val files = BookStore.list(this)
        emptyFiles.visibility = if (files.isEmpty()) TextView.VISIBLE else TextView.GONE
        val pad = (8 * resources.displayMetrics.density).toInt()

        for (file in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, pad, 0, pad)
            }
            val name = TextView(this).apply {
                text = "${file.nameWithoutExtension}\n${Formatter.formatShortFileSize(context, file.length())}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val share = MaterialButton(this).apply {
                text = "Gửi"
                setOnClickListener { share(file) }
            }
            val delete = MaterialButton(
                this, null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "Xoá"
                setOnClickListener { confirmDelete(file) }
            }
            row.addView(name)
            row.addView(share)
            row.addView(delete)
            filesContainer.addView(row)
        }
    }

    private fun share(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            putExtra(Intent.EXTRA_TITLE, file.name)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Gửi ${file.name} tới…"))
    }

    private fun shareAll() {
        val files = BookStore.list(this)
        if (files.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uriFor(it) }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Gửi ${files.size} file tới…"))
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xoá file?")
            .setMessage("${file.name} sẽ bị xoá khỏi FLazy. Bản đã gửi lên Drive (nếu có) vẫn giữ nguyên.")
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Xoá") { _, _ ->
                file.delete()
                refreshFiles()
            }
            .show()
    }
}
