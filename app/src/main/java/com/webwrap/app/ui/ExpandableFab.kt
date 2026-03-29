package com.webwrap.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FabItem(
    val icon: ImageVector,
    val label: String,
    val isToggle: Boolean = false,
    val isOn: Boolean = false,
    val activeColor: Color = Color(0xFF4CAF50),
    val inactiveColor: Color = Color(0xFF999999),
    val onClick: () -> Unit
)

@Composable
fun ExpandableFab(
    modifier: Modifier = Modifier,
    items: List<FabItem>,
    tabCount: Int = 0
) {
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(300), label = "rotation"
    )
    val fabAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.95f else 0.35f,
        animationSpec = tween(300), label = "alpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + slideInVertically(
                    initialOffsetY = { it / 2 }, animationSpec = tween(300)
                ),
                exit = fadeOut(tween(150)) + slideOutVertically(
                    targetOffsetY = { it / 2 }, animationSpec = tween(200)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    val rows = items.chunked(3)
                    rows.forEachIndexed { rowIndex, rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowItems.forEachIndexed { columnIndex, item ->
                                // // 1: Calculate index for staggered animation delay
                                val overallIndex = (rowIndex * 2) + columnIndex
                                val delay = overallIndex * 35

                                // // 2: Define visibility state INSIDE the item loop
                                val itemVisible = remember { mutableStateOf(false) }

                                // // 3: Use LaunchedEffect to handle the delay safely
                                LaunchedEffect(expanded) {
                                    if (expanded) {
                                        kotlinx.coroutines.delay(delay.toLong())
                                        itemVisible.value = true
                                    } else {
                                        itemVisible.value = false
                                    }
                                }

                                // // 4: Move AnimatedVisibility INSIDE the item loop
                                AnimatedVisibility(
                                    visible = itemVisible.value,
                                    enter = fadeIn(tween(150)) + scaleIn(
                                        initialScale = 0.6f,
                                        animationSpec = tween(200)
                                    ),
                                    exit = fadeOut(tween(100)) + scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec = tween(100)
                                    )
                                ) {
                                    CompactFabMenuItem(item = item) {
                                        item.onClick()
                                        if (!item.isToggle) expanded = false
                                    }
                                }
                            }
                        }
                    }
                }
            }



            // ✅ Main FAB with tab count badge
            Box {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = CircleShape,
                    color = if (expanded) Color(0xFFE57373).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = fabAlpha),
                    shadowElevation = if (expanded) 8.dp else 2.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.Close else Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = if (expanded) Color.White else Color(0xFF333333).copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp).rotate(rotation)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(90.dp))

                // ✅ Tab count badge
                if (tabCount > 1 && !expanded) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4FC3F7))
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$tabCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FabMenuItem(item: FabItem, onClick: () -> Unit) {
    val iconColor = if (item.isToggle && item.isOn) item.activeColor else item.inactiveColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            color = Color(0xFF1A1A2E).copy(alpha = 0.92f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = item.label,
                    color = if (item.isToggle && item.isOn) item.activeColor
                    else Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (item.isToggle) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (item.isOn) item.activeColor else Color(0xFF555555))
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            onClick = onClick, shape = CircleShape,
            color = if (item.isToggle && item.isOn) item.activeColor.copy(alpha = 0.2f)
            else Color(0xFF2A2A3E).copy(alpha = 0.9f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = item.icon, contentDescription = item.label,
                    tint = iconColor, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CompactFabMenuItem(item: FabItem, onClick: () -> Unit) {
    val iconColor = if (item.isToggle && item.isOn) item.activeColor else item.inactiveColor

    // Column makes it look much cleaner in a grid than a long Row
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp) // Fixed width keeps the grid aligned
    ) {

        // Standard Icon Button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (item.isToggle && item.isOn) item.activeColor.copy(alpha = 0.2f)
            else Color(0xFF2A2A3E).copy(alpha = 0.9f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                // Small dot for toggle state
                if (item.isToggle) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .clip(CircleShape)
                            .background(if (item.isOn) item.activeColor else Color.Gray)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Surface(
            color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.clickable { onClick() }
        ) {
            Text(
                text = item.label,//.split(":")[0], // Only show "Repeat" instead of "Repeat: OFF" to save space
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                maxLines = 1
            )
        }
    }
}