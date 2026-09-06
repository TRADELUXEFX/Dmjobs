package com.dmjobs.worker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Auto-send helper for WhatsApp.
 *
 * IMPORTANT: this only works while the user has manually enabled this service under
 * Settings > Accessibility > Installed apps > DM Jobs Worker. It cannot be turned on
 * from code. See requestAutoSend() below for how SendActivity triggers a tap.
 *
 * WhatsApp's internal view structure can change with any of their app updates, which
 * will silently break the button match below. This needs periodic re-checking against
 * whatever WhatsApp version your workers are running.
 */
class WhatsAppSendService : AccessibilityService() {

    companion object {
        // Set by SendActivity right before opening WhatsApp, so this service knows
        // a send is expected. Cleared after firing once, or after a timeout.
        @Volatile var pendingAutoSend = false
        // Set true only once SendActivity's own countdown timer finishes — the
        // service will not tap Send until both this and pendingAutoSend are true.
        @Volatile var countdownFinished = false
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!pendingAutoSend || !countdownFinished) return
        if (event?.packageName != WHATSAPP_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val sendButton = findSendButton(root)
        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pendingAutoSend = false
            countdownFinished = false
        }
    }

    // WhatsApp's send button typically exposes a content description of "Send".
    // This is the fragile part — WhatsApp can rename or restructure this at any time.
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.equals("Send", ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        pendingAutoSend = false
        countdownFinished = false
    }
}
