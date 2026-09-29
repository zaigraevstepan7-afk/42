package com.antigravity.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.antigravity.android.R
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val text: String)

@Composable
fun Blossom(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Icon(
        painter = painterResource(R.drawable.openai_blossom),
        contentDescription = null,
        tint = White,
        modifier = modifier.size(size),
    )
}

@Composable
fun WelcomeScreen(busy: Boolean, error: String?, onGoogle: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Blossom(size = 48.dp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .clickable(enabled = !busy, onClick = onGoogle)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.design_logo_google),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (busy) "Вход…" else "Продолжить с Google",
                    color = Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "На странице Google нажмите Sign in.",
                color = TextFaint,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    email: String,
    model: String,
    models: List<String>,
    messages: List<UiMessage>,
    conversations: List<Pair<String, String>>,
    busy: Boolean,
    onModel: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSend: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Sidebar, modifier = Modifier.width(300.dp)) {
                Column(Modifier.fillMaxHeight().padding(top = 12.dp)) {
                    DrawerRow("Новый чат") {
                        scope.launch { drawerState.close() }
                        onNewChat()
                    }
                    Text(
                        "Чаты",
                        color = TextFaint,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(conversations) { (id, title) ->
                            DrawerRow(title) {
                                scope.launch { drawerState.close() }
                                onOpenChat(id)
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(email.ifBlank { "Google" }, color = TextMain, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Выйти",
                            color = TextDim,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(onClick = onLogout),
                        )
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(Canvas).imePadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.sidebar),
                    contentDescription = "Меню",
                    tint = TextMain,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { scope.launch { drawerState.open() } }
                        .padding(12.dp),
                )
                Row(
                    Modifier.weight(1f).clickable { sheet = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("ChatGPT", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(
                        painter = painterResource(R.drawable.chevron_down),
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.pencil),
                    contentDescription = "Новый чат",
                    tint = TextMain,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onNewChat)
                        .padding(12.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Blossom(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(bottom = 4.dp)) {
                        items(messages) { message ->
                            when (message.role) {
                                "user" -> UserBubble(message.text)
                                "tool" -> Text(
                                    message.text,
                                    color = TextFaint,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                                else -> Text(
                                    message.text,
                                    color = TextMain,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (busy) {
                            item {
                                Text(
                                    "…",
                                    color = TextDim,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Composer)
                    .padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.plus),
                    contentDescription = null,
                    tint = TextMain,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    textStyle = TextStyle(color = TextMain, fontSize = 16.sp),
                    cursorBrush = SolidColor(White),
                    maxLines = 5,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Спросите что угодно", color = TextFaint, fontSize = 16.sp)
                        inner()
                    },
                )
                if (draft.isBlank()) {
                    Icon(
                        painter = painterResource(R.drawable.mic_filled),
                        contentDescription = null,
                        tint = TextMain,
                        modifier = Modifier.padding(horizontal = 6.dp).size(22.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.voice_button),
                        contentDescription = null,
                        tint = TextMain,
                        modifier = Modifier.padding(start = 2.dp, end = 10.dp).size(22.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(White)
                            .clickable(enabled = !busy) {
                                val text = draft.trim()
                                draft = ""
                                onSend(text)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_up),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            containerColor = Bubble,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text("Модель", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            models.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onModel(item)
                            sheet = false
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (item == model) Text("✓", color = TextMain, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Bubble)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DrawerRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        color = TextMain,
        fontSize = 16.sp,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

