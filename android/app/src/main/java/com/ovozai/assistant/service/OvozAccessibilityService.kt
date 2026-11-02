package com.ovozai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OvozAccessibilityService : AccessibilityService() {

    companion object {
        var instance: OvozAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("OvozAI", "OvozAccessibilityService ulandi!")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("OvozAI", "OvozAccessibilityService to'xtatildi!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d("OvozAI", "Accessibility Service to'xtatildi")
    }

    // --- GLOBAL TIZIM HARAKATLARI ---
    fun executeGlobal(action: String): Boolean {
        return when (action.lowercase()) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "lock" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            "screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else false
            }
            else -> false
        }
    }

    // --- EKRANDAGI MATNLI TUGMANI TOPISH VA BOSISH ---
    fun clickByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByTextRecursive(rootNode, targetText.lowercase().trim(), matchedNodes)

        for (node in matchedNodes) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }
            if (clickableNode != null && clickableNode.isClickable) {
                val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Log.d("OvozAI", "Bosildi: $targetText")
                    return true
                }
            }
        }

        // Agar bosiladigan element topilmasa, koordinatasi bo'yicha bosish (Tap)
        if (matchedNodes.isNotEmpty()) {
            val rect = Rect()
            matchedNodes[0].getBoundsInScreen(rect)
            return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
        }

        return false
    }

    private fun findNodesByTextRecursive(
        node: AccessibilityNodeInfo?,
        search: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (nodeText.contains(search) || nodeDesc.contains(search)) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            findNodesByTextRecursive(node.getChild(i), search, results)
        }
    }

    // --- MATN KIRITISH (INPUT TEXT) ---
    fun inputText(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        val inputNode = findFirstEditableNode(rootNode)
        if (inputNode != null) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val res = findFirstEditableNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    // --- AVTONOM XABAR YUBORISH (ILOVADA CHATGA YOZISH) ---
    fun autonomousSendMessage(recipient: String, messageText: String, onFinished: (Boolean) -> Unit) {
        serviceScope.launch {
            delay(1200) // Ilova ochilishini kutamiz

            // 1. Kontakt yoki chatni qidirish
            val clickedChat = clickByText(recipient)
            if (!clickedChat) {
                // Qidiruv tugmasini bosishga harakat qilamiz
                clickByText("qidiruv") || clickByText("search") || clickByText("найти")
                delay(800)
                inputText(recipient)
                delay(1000)
                clickByText(recipient)
            }

            delay(1000) // Chat ichiga kirishni kutamiz

            // 2. Xabar matnini yozish
            val typed = inputText(messageText)
            delay(500)

            // 3. Yuborish tugmasini topib bosish
            val sent = clickByText("yuborish") ||
                       clickByText("send") ||
                       clickByText("отправить") ||
                       clickSendIconButton()

            onFinished(sent || typed)
        }
    }

    private fun clickSendIconButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        findSendButtons(root, candidates)
        for (b in candidates) {
            if (b.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    private fun findSendButtons(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (desc.contains("send") || desc.contains("yubor") || desc.contains("отправ") || id.contains("send")) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            findSendButtons(node.getChild(i), list)
        }
    }

    // --- SKROLL (AYLANTIRISH) ---
    fun scrollScreen(isDown: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (isDown) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrollableNode = findFirstScrollableNode(rootNode)
        return scrollableNode?.performAction(action) ?: false
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val res = findFirstScrollableNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    // --- GESTURE BILAN BOSISH (TAP) ---
    fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    // --- EKRANDAGI BARCHA KO'RINIB TURGAN MATNLARNI YIG'ISH ---
    fun getVisibleScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return texts.distinct().take(30).joinToString(", ")
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length > 1) {
            list.add(t)
        }
        val cd = node.contentDescription?.toString()?.trim()
        if (!cd.isNullOrEmpty() && cd.length > 1) {
            list.add(cd)
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), list)
        }
    }
}
