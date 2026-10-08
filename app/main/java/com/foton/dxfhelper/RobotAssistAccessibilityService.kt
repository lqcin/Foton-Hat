package com.foton.dxfhelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.Locale


data class RobotSelection(
    val sectionNo: String,
    val startManhole: String,
    val endManhole: String,
    val lengthMeters: Double
)

class RobotAssistAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var targetPackage: String? = null
    private var targetViewId: String? = null
    private var lastLaunchAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ref = WeakReference(this)
    }

    override fun onDestroy() {
        if (ref?.get() === this) ref = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED && event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return

        val source = event.source ?: return
        if (!looksLikeBoruKesitTarget(source)) return

        val now = System.currentTimeMillis()
        if (now - lastLaunchAt < 1200) return
        lastLaunchAt = now
        targetPackage = pkg
        targetViewId = source.viewIdResourceName

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_FROM_ROBOT, true)
        }
        startActivity(intent)
    }

    fun hasRobotTarget(): Boolean = !targetPackage.isNullOrBlank()

    fun sendSelectionToRobot(selection: RobotSelection) {
        pending = selection
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ fillRobotFields(selection) }, 700)
        handler.postDelayed({ fillRobotFields(selection) }, 1400)
    }

    private fun fillRobotFields(selection: RobotSelection) {
        val root = rootInActiveWindow ?: return
        if (targetPackage != null && root.packageName?.toString() != targetPackage) return

        var success = false
        val id = targetViewId
        if (!id.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            nodes.firstOrNull()?.let { success = setText(it, selection.sectionNo) || success }
        }

        success = fillByLabel(root, "Boru Kesit No", selection.sectionNo) || success
        fillByLabel(root, "Başlangıç Bacası", selection.startManhole)
        fillByLabel(root, "Bitiş Bacası", selection.endManhole)
        val len = String.format(Locale.US, "%.2f", selection.lengthMeters).replace('.', ',')
        fillByLabel(root, "Boru Uzunluğu", len)

        if (success) {
            Toast.makeText(this, "${selection.sectionNo} aktarıldı", Toast.LENGTH_SHORT).show()
            pending = null
        } else {
            Toast.makeText(this, "Robot alanına otomatik yazılamadı. Boru Kesit No panoda.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fillByLabel(root: AccessibilityNodeInfo, label: String, value: String): Boolean {
        val flat = mutableListOf<AccessibilityNodeInfo>()
        flatten(root, flat)

        flat.firstOrNull { node ->
            node.isEditable && listOf(node.hintText, node.contentDescription, node.text)
                .filterNotNull().any { normalize(it.toString()).contains(normalize(label)) }
        }?.let { if (setText(it, value)) return true }

        val labelIndex = flat.indexOfFirst { node ->
            listOf(node.text, node.contentDescription, node.hintText)
                .filterNotNull().any { normalize(it.toString()).contains(normalize(label)) }
        }
        if (labelIndex >= 0) {
            for (i in labelIndex until minOf(flat.size, labelIndex + 14)) {
                val node = flat[i]
                if (node.isEditable && setText(node, value)) return true
            }
        }
        return false
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        if (!node.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun looksLikeBoruKesitTarget(source: AccessibilityNodeInfo): Boolean {
        val probes = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.toString()?.let { probes += it }
            n.hintText?.toString()?.let { probes += it }
            n.contentDescription?.toString()?.let { probes += it }
        }
        collect(source)
        collect(source.parent)
        val p = source.parent
        if (p != null) {
            for (i in 0 until p.childCount) collect(p.getChild(i))
        }
        return probes.any { normalize(it).contains("boru kesit no") }
    }

    private fun flatten(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out += node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { flatten(it, out) }
        }
    }

    private fun normalize(s: String): String = s.lowercase(Locale("tr", "TR"))
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .trim()

    companion object {
        private var ref: WeakReference<RobotAssistAccessibilityService>? = null
        val instance: RobotAssistAccessibilityService? get() = ref?.get()
        @Volatile var pending: RobotSelection? = null
    }
}
