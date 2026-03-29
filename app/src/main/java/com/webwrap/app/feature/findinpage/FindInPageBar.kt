package com.webwrap.app.feature.findinpage

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable bar for "Find in Page" functionality.
 * Shows search input with next/previous/close buttons.
 */
@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Search input field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        "Find in page...",
                        color = Color(0xFF556677),
                        fontSize = 13.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF252538),
                    unfocusedContainerColor = Color(0xFF252538),
                    focusedBorderColor = Color(0xFF4FC3F7),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF4FC3F7)
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onFindNext() }
                )
            )

            // Previous match button
            IconButton(onClick = onFindPrevious) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Next match button
            IconButton(onClick = onFindNext) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Helper object for WebView find-in-page operations.
 * Wraps WebView's findAllAsync and findNext methods.
 */
object FindInPageHelper {

    private var currentIndex = 0

    /**
     * Start searching for text in the WebView.
     * Uses findAllAsync for async search.
     */
    @Suppress("DEPRECATION")
    fun find(webView: WebView?, query: String) {
        if (query.isBlank()) {
            clearFind(webView)
            return
        }
        currentIndex = 0
        webView?.findAllAsync(query)
    }

    /**
     * Navigate to next match in WebView.
     */
    fun findNext(webView: WebView?) {
        webView?.findNext(true)
        currentIndex++
    }

    /**
     * Navigate to previous match in WebView.
     */
    fun findPrevious(webView: WebView?) {
        webView?.findNext(false)
        if (currentIndex > 0) currentIndex--
    }

    /**
     * Clear all search highlights.
     */
    fun clearFind(webView: WebView?) {
        webView?.clearMatches()
        currentIndex = 0
    }
}