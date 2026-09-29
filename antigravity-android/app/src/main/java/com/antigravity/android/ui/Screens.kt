package com.antigravity.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import com.antigravity.android.R
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Blossom(size = 64.dp)
                Icon(
                    painter = painterResource(R.drawable.design_logo_chatgpt),
                    contentDescription = "ChatGPT",
                    tint = White,
                    modifier = Modifier.height(24.dp).width(137.45.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .shadow(1.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .clickable(enabled = !busy, onClick = onGoogle)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.design_logo_google),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (busy) "Вход…" else "Продолжить с Google",
                    color = Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!error.isNullOrBlank()) {
                Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
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
    onSettings: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var temporary by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    val barBlur = HazeStyle(
        backgroundColor = Canvas.copy(alpha = 0.72f),
        tints = listOf(HazeTint(Canvas.copy(alpha = 0.55f))),
        blurRadius = 20.dp,
        noiseFactor = 0.08f,
    )
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Sidebar, modifier = Modifier.fillMaxWidth(0.86f)) {
                Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ChatGPT", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        CircleIcon(R.drawable.ds_search, "Поиск") {}
                        Spacer(Modifier.width(8.dp))
                        CircleIcon(R.drawable.ds_refresh, "Временный чат") { scope.launch { drawerState.close() } }
                    }
                    SideRow(R.drawable.ds_image, "Изображения")
                    SideRow(R.drawable.ds_library, "Библиотека")
                    SideRow(R.drawable.ds_folder, "Проекты")
                    SideRow(R.drawable.ds_remote, "Remote")
                    SideRow(R.drawable.ds_clock, "Запланировано")
                    SideRow(R.drawable.ds_plugin, "Плагины")
                    Spacer(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(0.5.dp).background(Hairline))
                    LazyColumn(Modifier.weight(1f)) {
                        items(conversations) { (id, title) ->
                            Text(
                                title,
                                color = TextMain,
                                fontSize = 16.sp,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch { drawerState.close() }
                                    onOpenChat(id)
                                }.padding(vertical = 12.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(Blue).clickable {
                                scope.launch { drawerState.close() }
                                onNewChat()
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ds_new_chat), contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                            Text("Чат", color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        CircleIcon(R.drawable.ds_gear, "Настройки") {
                            scope.launch { drawerState.close() }
                            onSettings()
                        }
                        Spacer(Modifier.width(8.dp))
                        CircleIcon(R.drawable.ds_voice, "Голос") {}
                    }
                }
            }
        },
    ) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize().background(Canvas).imePadding()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                if (messages.isEmpty()) {
                    if (temporary) {
                        Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Временный чат", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text("Этот чат не появится в истории.", color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                            Text("Узнать больше", color = TextDim, fontSize = 16.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("С персонализацией", color = TextMain, fontSize = 16.sp)
                                Icon(painterResource(R.drawable.ds_chevron), contentDescription = null, tint = TextMain, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                        }
                    } else {
                        Column(Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = bottomInset + 84.dp)) {
                            SuggestRow(R.drawable.ds_image_spark, "Создать изображение") { draft = "Создать изображение " }
                            SuggestRow(R.drawable.ds_pencil, "Напиши или отредактируй") { draft = "Напиши или отредактируй " }
                            SuggestRow(R.drawable.ds_globe2, "Искать в интернете") { draft = "Искать в интернете " }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = topInset + 52.dp, bottom = bottomInset + 76.dp),
                    ) {
                        itemsIndexed(messages) { index, message ->
                            when (message.role) {
                                "user" -> UserBubble(message.text)
                                "tool" -> Text(
                                    message.text,
                                    color = TextFaint,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                                else -> AnswerText(
                                    message.text,
                                    reveal = index == messages.lastIndex,
                                )
                            }
                        }
                        if (busy) {
                            item { ThinkingDots() }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(R.drawable.ds_sidebar, "Меню") { scope.launch { drawerState.open() } }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(BlueSoft)
                        .clickable { sheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ds_sparkle), contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                    Text("Подключить", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                CircleIcon(R.drawable.ds_refresh, "Временный чат") { temporary = !temporary }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Canvas))),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(26.dp))
                    .background(Composer)
                    .padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ds_plus),
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
                        if (draft.isEmpty()) Text(if (temporary) "Временный чат" else "Спросить ChatGPT", color = TextFaint, fontSize = 16.sp)
                        inner()
                    },
                )
                AnimatedContent(
                    targetState = draft.isBlank() && !temporary,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "composer-trailing",
                ) { empty ->
                    if (empty && !temporary) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.mic_filled),
                                contentDescription = null,
                                tint = TextMain,
                                modifier = Modifier.padding(horizontal = 6.dp).size(22.dp),
                            )
                            Box(
                                Modifier.padding(end = 4.dp).size(36.dp).clip(CircleShape).background(Blue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ds_voice), contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .padding(6.dp)
                                .size(32.dp)
                            .clip(CircleShape)
                            .background(if (draft.isBlank()) Card else Blue)
                                .clickable(enabled = !busy) {
                                    val text = draft.trim()
                                    draft = ""
                                    onSend(text)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ds_arrow_up),
                                contentDescription = null,
                                tint = if (draft.isBlank()) TextDim else White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
private fun AnswerText(text: String, reveal: Boolean) {
    var count by remember(text) { mutableIntStateOf(if (reveal) 0 else text.length) }
    LaunchedEffect(text, reveal) {
        if (!reveal) {
            count = text.length
            return@LaunchedEffect
        }
        while (count < text.length) {
            count = minOf(text.length, count + 2)
            kotlinx.coroutines.delay(16)
        }
    }
    Text(
        text.take(count),
        color = TextMain,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 } + scaleIn(tween(220), initialScale = 0.96f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text,
                color = TextMain,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Bubble)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CircleIcon(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = TextMain, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SideRow(icon: Int, title: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = TextMain, modifier = Modifier.size(22.dp))
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun SuggestRow(icon: Int, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = TextDim, modifier = Modifier.size(22.dp))
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}


