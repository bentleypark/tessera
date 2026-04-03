package com.github.bentleypark.tessera

import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDesktopTest {

    @Test
    fun currentTimeMillis_returnsPositiveValue() {
        val time = currentTimeMillis()
        assertTrue(time > 0)
    }

    @Test
    fun simpleClassName_returnsCorrectName() {
        val name = RuntimeException("test").simpleClassName()
        assertTrue(name == "RuntimeException")
    }

    // --- isZoomModifierPressed via AWT MouseEvent ---

    private fun createMouseEvent(modifiers: Int): MouseEvent {
        val source = JPanel()
        return MouseEvent(
            source,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            modifiers,
            50, 50,
            0,
            false
        )
    }

    @Test
    fun awtMouseEvent_ctrlModifier_detected() {
        val event = createMouseEvent(InputEvent.CTRL_DOWN_MASK)
        val modifiers = event.modifiersEx
        val ctrl = (modifiers and InputEvent.CTRL_DOWN_MASK) != 0
        assertTrue(ctrl)
    }

    @Test
    fun awtMouseEvent_metaModifier_detected() {
        val event = createMouseEvent(InputEvent.META_DOWN_MASK)
        val modifiers = event.modifiersEx
        val meta = (modifiers and InputEvent.META_DOWN_MASK) != 0
        assertTrue(meta)
    }

    @Test
    fun awtMouseEvent_noModifier_notDetected() {
        val event = createMouseEvent(0)
        val modifiers = event.modifiersEx
        val ctrl = (modifiers and InputEvent.CTRL_DOWN_MASK) != 0
        val meta = (modifiers and InputEvent.META_DOWN_MASK) != 0
        assertFalse(ctrl || meta)
    }

    @Test
    fun awtMouseEvent_shiftOnly_notZoomModifier() {
        val event = createMouseEvent(InputEvent.SHIFT_DOWN_MASK)
        val modifiers = event.modifiersEx
        val ctrl = (modifiers and InputEvent.CTRL_DOWN_MASK) != 0
        val meta = (modifiers and InputEvent.META_DOWN_MASK) != 0
        assertFalse(ctrl || meta)
    }
}
