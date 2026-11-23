package org.blindsystems.cyclope.voicekit.android

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat

/** Shared native-widget helpers. They preserve platform AccessibilityNodeInfo role/checked state. */
object VoiceAccessibility {
    fun label(
        view: View,
        description: CharSequence,
    ) {
        view.contentDescription = description
        view.minimumHeight = maxOf(view.minimumHeight, (48 * view.resources.displayMetrics.density).toInt())
    }

    fun heading(view: View) {
        ViewCompat.setAccessibilityHeading(view, true)
    }

    fun polite(
        view: View,
        focusable: Boolean = false,
    ) {
        view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (focusable) view.isFocusable = true
    }

    /** The native POLITE region emits a content-change AccessibilityEvent. Do not duplicate it. */
    fun publishPolite(
        view: TextView,
        text: CharSequence,
    ) {
        polite(view)
        if (view.text.toString() != text.toString()) view.text = text
    }

    /** Only for an explicit user request; automatic announcements use semantic live-region changes. */
    fun announceRequested(
        view: View,
        text: CharSequence,
    ) {
        view.announceForAccessibility(text)
    }

    fun excludeFromTraversal(
        view: View,
        descendants: Boolean = false,
    ) {
        view.importantForAccessibility = if (descendants) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun showDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.show()
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.let { label(it, it.text) }
        }
        dialog.listView?.let { list ->
            fun describeRow(row: View) {
                if (row is TextView) label(row, row.text)
            }
            for (index in 0 until list.childCount) describeRow(list.getChildAt(index))
            list.setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(
                        parent: View?,
                        child: View?,
                    ) {
                        child?.let(::describeRow)
                    }

                    override fun onChildViewRemoved(
                        parent: View?,
                        child: View?,
                    ) = Unit
                },
            )
        }
        return dialog
    }
}
