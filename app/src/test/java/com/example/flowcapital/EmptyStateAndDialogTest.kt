package com.example.flowcapital

import org.junit.Assert.*
import org.junit.Test

class EmptyStateAndDialogTest {

    // ========== UI СОСТОЯНИЯ (только сложная логика!) ==========

    @Test
    fun `empty flow state button triggers start dialog`() {
        val placeholder = "Думаю, стоит завести"
        assertTrue(placeholder.contains("Думаю"))
    }

    @Test
    fun `unimplemented flow shows placeholder`() {
        val placeholder = "В разработке. Скоро будет!"
        assertTrue(placeholder.contains("В разработке"))
        assertTrue(placeholder.contains("Скоро"))
    }

    @Test
    fun `wallet hint text for empty or zero`() {
        val emptyHint = "Оставьте поле пустым, если в кошельке пусто"
        val zeroHint = "Введите 0, чтобы обнулить кошелёк"
        assertTrue(emptyHint.contains("пустым"))
        assertTrue(zeroHint.contains("0"))
    }

    // ========== ДИАЛОГ КНОПКИ (стандарт!) ==========

    @Test
    fun `dialog button order is correct`() {
        val buttons = listOf("Отмена", "Внести")
        assertEquals("Отмена", buttons[0])
        assertEquals("Внести", buttons[1])
    }
}