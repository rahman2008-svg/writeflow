package com.example.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp

object DocumentFormatter {
    
    fun toAnnotatedString(text: String, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = text.length
            
            val boldStack = mutableListOf<Int>()
            val italicStack = mutableListOf<Int>()
            val underlineStack = mutableListOf<Int>()
            val colorStack = mutableListOf<Pair<Int, Color>>()
            val bgStack = mutableListOf<Pair<Int, Color>>()
            val sizeStack = mutableListOf<Pair<Int, Float>>()

            while (i < len) {
                if (text[i] == '<') {
                    val closingIndex = text.indexOf('>', i)
                    if (closingIndex != -1) {
                        val tagContent = text.substring(i + 1, closingIndex).trim()
                        val isClosing = tagContent.startsWith("/")
                        val tag = if (isClosing) tagContent.substring(1).lowercase() else tagContent.lowercase()
                        
                        var handled = true
                        if (!isClosing) {
                            when {
                                tag == "b" -> boldStack.add(length)
                                tag == "i" -> italicStack.add(length)
                                tag == "u" -> underlineStack.add(length)
                                tag.startsWith("color:") -> {
                                    val hex = tag.substringAfter("color:")
                                    val color = parseHexColor(hex, defaultColor)
                                    colorStack.add(length to color)
                                }
                                tag.startsWith("bg:") -> {
                                    val hex = tag.substringAfter("bg:")
                                    val color = parseHexColor(hex, Color.Yellow)
                                    bgStack.add(length to color)
                                }
                                tag.startsWith("h1") -> sizeStack.add(length to 24f)
                                tag.startsWith("h2") -> sizeStack.add(length to 20f)
                                tag.startsWith("h3") -> sizeStack.add(length to 18f)
                                else -> handled = false
                            }
                        } else {
                            when (tag) {
                                "b" -> {
                                    if (boldStack.isNotEmpty()) {
                                        val start = boldStack.removeAt(boldStack.size - 1)
                                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                                    }
                                }
                                "i" -> {
                                    if (italicStack.isNotEmpty()) {
                                        val start = italicStack.removeAt(italicStack.size - 1)
                                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                                    }
                                }
                                "u" -> {
                                    if (underlineStack.isNotEmpty()) {
                                        val start = underlineStack.removeAt(underlineStack.size - 1)
                                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, length)
                                    }
                                }
                                "color" -> {
                                    if (colorStack.isNotEmpty()) {
                                        val (start, color) = colorStack.removeAt(colorStack.size - 1)
                                        addStyle(SpanStyle(color = color), start, length)
                                    }
                                }
                                "bg" -> {
                                    if (bgStack.isNotEmpty()) {
                                        val (start, color) = bgStack.removeAt(bgStack.size - 1)
                                        addStyle(SpanStyle(background = color), start, length)
                                    }
                                }
                                "h1", "h2", "h3" -> {
                                    if (sizeStack.isNotEmpty()) {
                                        val (start, size) = sizeStack.removeAt(sizeStack.size - 1)
                                        addStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold), start, length)
                                    }
                                }
                                else -> handled = false
                            }
                        }
                        
                        if (handled) {
                            i = closingIndex + 1
                            continue
                        }
                    }
                }
                
                append(text[i])
                i++
            }
            
            while (boldStack.isNotEmpty()) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStack.removeAt(boldStack.size - 1), length)
            }
            while (italicStack.isNotEmpty()) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStack.removeAt(italicStack.size - 1), length)
            }
            while (underlineStack.isNotEmpty()) {
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStack.removeAt(underlineStack.size - 1), length)
            }
            while (colorStack.isNotEmpty()) {
                val (start, color) = colorStack.removeAt(colorStack.size - 1)
                addStyle(SpanStyle(color = color), start, length)
            }
            while (bgStack.isNotEmpty()) {
                val (start, color) = bgStack.removeAt(bgStack.size - 1)
                addStyle(SpanStyle(background = color), start, length)
            }
            while (sizeStack.isNotEmpty()) {
                val (start, size) = sizeStack.removeAt(sizeStack.size - 1)
                addStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold), start, length)
            }
        }
    }

    private fun parseHexColor(hex: String, default: Color): Color {
        return try {
            val cleanHex = hex.trim().removePrefix("#")
            if (cleanHex.length == 6) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else if (cleanHex.length == 8) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }
    }

    // Helper to calculate statistics
    fun getWordCount(text: String): Int {
        val cleanText = text.replace(Regex("<[^>]*>"), " ").trim()
        if (cleanText.isEmpty()) return 0
        return cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    fun getCharCount(text: String): Int {
        val cleanText = text.replace(Regex("<[^>]*>"), "")
        return cleanText.length
    }
}
