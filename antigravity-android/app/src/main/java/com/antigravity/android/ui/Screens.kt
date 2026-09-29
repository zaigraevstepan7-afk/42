package com.antigravity.android.ui

import android.graphics.BitmapFactory
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.android.R
import com.antigravity.android.net.Citation
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PendingFile(val name: String, val mime: String, val bytes: ByteArray)

enum class ReplyPhase { Waiting, Searching, Streaming, Done }

data class UiMessage(
    val role: String,
    val text: String,
    val id: String = UUID.randomUUID().toString(),
    val sending: Boolean = false,
    val phase: ReplyPhase = ReplyPhase.Done,
    val searchLabel: String? = null,
    val thinking: Boolean = false,
    val citations: List<Citation> = emptyList(),
    val images: List<ByteArray> = emptyList(),
    val complete: Boolean = true,
)

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
    activeId: String,
    busy: Boolean,
    onModel: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    incoming: PendingFile? = null,
    onIncomingConsumed: () -> Unit = {},
    onSend: (String, List<PendingFile>, Boolean) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startDictation(context) { spoken ->
                draft = if (draft.isBlank()) spoken else "$draft $spoken"
            }
        }
    }
    val dictate = {
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    var files by remember { mutableStateOf(listOf<PendingFile>()) }
    var deepThink by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            files = files + PendingFile("photo.jpg", "image/jpeg", stream.toByteArray())
        }
        attachMenu = false
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto.launch(null)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            readPending(context, uri, "photo")?.let { files = files + it }
        }
        attachMenu = false
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            readPending(context, uri, "file")?.let { files = files + it }
        }
        attachMenu = false
    }
    LaunchedEffect(incoming?.name, incoming?.bytes?.size) {
        val file = incoming ?: return@LaunchedEffect
        files = files + file
        onIncomingConsumed()
    }
    var sheet by remember { mutableStateOf(false) }
    var connect by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var temporary by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<Citation>?>(null) }
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size, last?.text, last?.phase, last?.sending, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.51f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Sidebar,
                drawerShape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth(0.80f),
            ) {
                Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ChatGPT", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        CircleIcon(R.drawable.ds_search, "Поиск", size = 44.dp, iconSize = 27.dp) {}
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        onOpenChat(id)
                                    }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(AccentGreen)
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onNewChat()
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ds_chat), contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
                            Text("Чат", color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        CircleIcon(R.drawable.ds_gear, "Настройки", size = 44.dp, iconSize = 22.dp) {
                            scope.launch { drawerState.close() }
                            onSettings()
                        }
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
                    } else if (draft.isBlank()) {
                        Column(Modifier.align(Alignment.BottomCenter).padding(start = 44.dp, end = 44.dp, bottom = bottomInset + 70.dp)) {
                            SuggestRow(R.drawable.ds_image_spark, "Создать изображение") { draft = "Создать изображение " }
                            SuggestRow(R.drawable.ds_pencil, "Напиши или отредактируй") { draft = "Напиши или отредактируй " }
                            SuggestRow(R.drawable.ds_globe2, "Искать в интернете") { draft = "Искать в интернете " }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = topInset + 52.dp, bottom = bottomInset + 88.dp),
                    ) {
                        itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                            when (message.role) {
                                "user" -> UserBubble(message.text, message.sending, message.images)
                                "tool" -> Text(
                                    message.text,
                                    color = TextFaint,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                                else -> AssistantTurn(
                                    message = message,
                                    showActions = message.complete && message.phase == ReplyPhase.Done && index == messages.indexOfLast { it.role == "model" },
                                    onCopy = { copyText(context, message.text) },
                                    onShare = { shareText(context, message.text) },
                                    onRegenerate = onRegenerate,
                                    onSources = { sources = message.citations },
                                )
                            }
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
                if (messages.isEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BlueSoft)
                            .clickable { connect = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ds_sparkle_filled), contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                        Text("Подключить", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CircleIcon(R.drawable.ds_refresh_loop, "Временный чат") { temporary = !temporary }
                } else {
                    Spacer(Modifier.weight(1f))
                    CircleIcon(R.drawable.ds_more, "Ещё", shadow = false) { }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Canvas))),
            )
            if (messages.isNotEmpty() && listState.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 72.dp)
                        .size(44.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(White)
                        .clickable { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ds_chevron), contentDescription = "Вниз", tint = TextMain, modifier = Modifier.size(22.dp))
                }
            }
            val stacked = files.isNotEmpty()
            val writing = draft.isNotBlank() || stacked
            val actionColor = AccentGreen
            val prompt = when {
                temporary -> "Временный чат"
                messages.isNotEmpty() -> "Ответить ChatGPT"
                else -> "Спросить ChatGPT"
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 44.dp, end = 44.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x22000000), spotColor = Color(0x14000000))
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(28.dp))
                    .background(Composer)
                    .padding(top = if (stacked) 10.dp else 0.dp),
            ) {
                if (stacked) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                        files.forEachIndexed { index, file ->
                            if (file.mime.startsWith("image/")) {
                                ShotPreview(file.bytes, removable = true) {
                                    files = files.filterIndexed { i, _ -> i != index }
                                }
                            } else {
                                Text(
                                    file.name,
                                    color = TextMain,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Bubble)
                                        .clickable { files = files.filterIndexed { i, _ -> i != index } }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp, max = 140.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                        textStyle = TextStyle(color = TextMain, fontSize = 16.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(AccentGreen),
                        maxLines = 6,
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(prompt, color = TextFaint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        },
                    )
                }
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ds_plus),
                        contentDescription = "Добавить",
                        tint = TextMain,
                        modifier = Modifier.padding(horizontal = 10.dp).size(22.dp).clickable { attachMenu = !attachMenu },
                    )
                    if (!stacked) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = TextMain, fontSize = 16.sp),
                            cursorBrush = SolidColor(if (messages.isEmpty()) Blue else AccentGreen),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(prompt, color = TextFaint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                inner()
                            },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (!busy) {
                        Icon(
                            painterResource(R.drawable.ds_mic),
                            contentDescription = "Голос в текст",
                            tint = TextMain,
                            modifier = Modifier.padding(end = 8.dp).size(22.dp).clickable { dictate() },
                        )
                    }
                    AnimatedContent(
                        targetState = when {
                            busy -> "stop"
                            writing || temporary -> "send"
                            else -> "voice"
                        },
                        transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
                        label = "send-slot",
                    ) { slot ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(actionColor)
                                .clickable {
                                    when (slot) {
                                        "stop" -> onStop()
                                        "send" -> {
                                            val text = draft.trim()
                                            if (text.isNotEmpty() || files.isNotEmpty()) {
                                                val attached = files
                                                draft = ""
                                                files = emptyList()
                                                expanded = false
                                                attachMenu = false
                                                onSend(text, attached, deepThink)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (slot) {
                                "stop" -> Box(Modifier.size(11.dp).clip(RoundedCornerShape(1.5.dp)).background(White))
                                "send" -> Icon(painterResource(R.drawable.ds_arrow_up), contentDescription = "Отправить", tint = White, modifier = Modifier.size(18.dp))
                                else -> Icon(painterResource(R.drawable.voice_button), contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (attachMenu) {
                Box(Modifier.fillMaxSize().clickable { attachMenu = false })
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 74.dp)
                        .width(280.dp)
                        .shadow(18.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(White)
                        .padding(vertical = 8.dp),
                ) {
                    AttachRow(R.drawable.ds_camera, "Камера") { cameraPermission.launch(Manifest.permission.CAMERA) }
                    AttachRow(R.drawable.ds_image, "Фото") { pickPhoto.launch("image/*") }
                    AttachRow(R.drawable.ds_clip, "Файлы") { pickFile.launch("*/*") }
                    AttachRow(R.drawable.ds_plugin, "Плагины") {
                        attachMenu = false
                        connect = true
                    }
                    AttachRow(R.drawable.ds_brain, "Размышлять глубже", checked = deepThink) {
                        deepThink = !deepThink
                        attachMenu = false
                    }
                }
            }
            if (expanded) {
                PredictiveBackSlide(onBack = { expanded = false }) {
                    ExpandedComposer(
                        text = draft,
                        onText = { draft = it },
                        onClose = { expanded = false },
                        onSend = {
                            val text = draft.trim()
                        if (text.isNotEmpty() && !busy) {
                            draft = ""
                            expanded = false
                            val attached = files
                            files = emptyList()
                            onSend(text, attached, deepThink)
                        }
                        },
                    )
                }
            }
        }
    }

    if (connect) {
        ModalBottomSheet(onDismissRequest = { connect = false }, containerColor = White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text("Подключить", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Text("Подключите приложения, чтобы пользоваться ими в чате.", color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
    val openSources = sources
    if (openSources != null) {
        ModalBottomSheet(
            onDismissRequest = { sources = null },
            containerColor = White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text("Источники", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (openSources.isEmpty()) {
                Text("Источники появятся, когда модель найдёт страницы в сети.", color = TextDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            } else {
                openSources.forEach { citation ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openUrl(context, citation.url) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ds_globe2), contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(citation.label, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(citation.url, color = TextFaint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
private fun AssistantTurn(
    message: UiMessage,
    showActions: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onSources: () -> Unit,
) {
    val showSearch = message.phase == ReplyPhase.Searching || (message.phase == ReplyPhase.Streaming && message.text.isEmpty())
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = message.phase == ReplyPhase.Waiting && message.text.isEmpty(), enter = fadeIn(tween(160)), exit = fadeOut(tween(180))) {
            ThinkingDots()
        }
        AnimatedVisibility(visible = showSearch, enter = fadeIn(tween(220)), exit = fadeOut(tween(260))) {
            Column {
                SearchStatus(message.searchLabel ?: "Поиск в интернете...")
                if (message.thinking) ThinkingShimmer()
            }
        }
        if (message.text.isNotEmpty()) {
            AnswerMarkdown(message.text, message.citations, streaming = message.phase == ReplyPhase.Streaming)
        }
        AnimatedVisibility(visible = showActions, enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) {
            ActionBar(hasSources = message.citations.isNotEmpty(), onCopy = onCopy, onShare = onShare, onRegenerate = onRegenerate, onSources = onSources)
        }
    }
}

@Composable
private fun SearchStatus(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ds_globe2), contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = TextDim,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp, end = 10.dp),
        )
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp, color = TextDim, trackColor = Color.Transparent)
    }
}

@Composable
private fun ThinkingShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -160f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    Text(
        "Обдумывание",
        fontSize = 15.sp,
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFB4B4B4),
                    Color(0xFFB4B4B4),
                    Color(0xFFEDEDED),
                    Color(0xFF1A1A1A),
                    Color(0xFFEDEDED),
                    Color(0xFFB4B4B4),
                ),
                start = Offset(shift, 0f),
                end = Offset(shift + 170f, 0f),
            ),
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerMarkdown(text: String, citations: List<Citation>, streaming: Boolean) {
    val context = LocalContext.current
    val blocks = remember(text) { markdownBlocks(text) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    block.text,
                    color = TextMain,
                    fontSize = if (block.level <= 1) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (block.level <= 1) 28.sp else 24.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                is MdBlock.Paragraph -> Text(
                    inlineMarkdown(block.text),
                    color = TextMain,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        if (citations.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                citations.take(6).forEach { citation ->
                    Text(
                        citation.label,
                        color = Color(0xFF5C5C5C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFEEEEEE))
                            .clickable { openUrl(context, citation.url) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (streaming) Spacer(Modifier.height(4.dp))
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun markdownBlocks(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        val value = paragraph.toString().trim()
        if (value.isNotEmpty()) out += MdBlock.Paragraph(value)
        paragraph.clear()
    }
    text.split("\n").forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.startsWith("### ") -> {
                flush()
                out += MdBlock.Heading(3, trimmed.removePrefix("### ").trim())
            }
            trimmed.startsWith("## ") -> {
                flush()
                out += MdBlock.Heading(2, trimmed.removePrefix("## ").trim())
            }
            trimmed.startsWith("# ") -> {
                flush()
                out += MdBlock.Heading(1, trimmed.removePrefix("# ").trim())
            }
            trimmed.isEmpty() -> flush()
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append("• ").append(trimmed.drop(2))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    flush()
    return out
}

private fun inlineMarkdown(text: String) = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var last = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun ActionBar(
    hasSources: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onSources: () -> Unit,
) {
    var vote by remember { mutableIntStateOf(0) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(R.drawable.ds_copy, "Копировать", onClick = onCopy)
        ActionIcon(R.drawable.ds_like, "Нравится", tint = if (vote == 1) Blue else TextFaint, onClick = { vote = if (vote == 1) 0 else 1 })
        ActionIcon(R.drawable.ds_dislike, "Не нравится", tint = if (vote == -1) Blue else TextFaint, onClick = { vote = if (vote == -1) 0 else -1 })
        ActionIcon(R.drawable.ds_speaker, "Озвучить", onClick = {})
        ActionIcon(R.drawable.ds_share, "Поделиться", onClick = onShare)
        ActionIcon(R.drawable.ds_more, "Ещё", onClick = { if (hasSources) onSources() else onRegenerate() })
    }
}

@Composable
private fun ActionIcon(icon: Int, label: String, onClick: () -> Unit, tint: Color = TextFaint) {
    Icon(
        painterResource(icon),
        contentDescription = label,
        tint = tint,
        modifier = Modifier.padding(end = 14.dp).size(18.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val scale by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .size(10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Blue),
    )
}

@Composable
private fun ShotPreview(bytes: ByteArray, removable: Boolean, onRemove: () -> Unit = {}) {
    val image = remember(bytes) { decodePreview(bytes) }
    Box(Modifier.padding(end = 8.dp).size(width = 104.dp, height = 132.dp)) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Bubble))
        }
        if (removable) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC1A1A1A))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun decodePreview(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 900 && sample < 16) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

@Composable
private fun UserBubble(text: String, sending: Boolean, images: List<ByteArray>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 } + scaleIn(tween(220), initialScale = 0.96f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            images.forEach { bytes ->
                ShotPreview(bytes, removable = false)
            }
            if (text.isNotBlank()) {
                Text(
                    text,
                    color = TextMain,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .padding(top = if (images.isEmpty()) 0.dp else 6.dp)
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MintBubble)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            AnimatedVisibility(visible = sending, enter = fadeIn(tween(80)), exit = fadeOut(tween(180))) {
                Text("Отправка", color = TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
            }
        }
    }
}

private fun startDictation(context: Context, onText: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val spoken = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) onText(spoken)
            recognizer.destroy()
        }
        override fun onError(error: Int) {
            recognizer.destroy()
        }
        override fun onReadyForSpeech(params: Bundle) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle) = Unit
        override fun onEvent(eventType: Int, params: Bundle) = Unit
    })
    recognizer.startListening(intent)
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ChatGPT", text))
}

private fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Поделиться",
        ),
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun ExpandedComposer(text: String, onText: (String) -> Unit, onClose: () -> Unit, onSend: () -> Unit) {
    Box(Modifier.fillMaxSize().background(White).statusBarsPadding().imePadding().navigationBarsPadding()) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 88.dp),
            textStyle = TextStyle(color = TextMain, fontSize = 18.sp, lineHeight = 26.sp),
            cursorBrush = SolidColor(Blue),
        )
        Icon(
            painterResource(R.drawable.ds_expand),
            contentDescription = "Свернуть",
            tint = TextMain,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(22.dp).clickable(onClick = onClose),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Black)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ds_arrow_up), contentDescription = "Отправить", tint = White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CircleIcon(
    icon: Int,
    label: String,
    shadow: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .then(if (shadow) Modifier.shadow(2.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = TextMain, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun SideRow(icon: Int, title: String) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = TextMain, modifier = Modifier.size(22.dp))
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 15.dp))
    }
}

@Composable
private fun AttachRow(icon: Int, title: String, checked: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (checked) BlueSoft else PlusGray),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = if (checked) Blue else TextMain, modifier = Modifier.size(20.dp))
        }
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (checked) Text("✓", color = Blue, fontSize = 16.sp)
    }
}

private suspend fun readPending(context: Context, uri: Uri, fallback: String): PendingFile? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: fallback
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
    if (bytes.size > 4_000_000) return@withContext PendingFile(name, mime, bytes.copyOf(4_000_000))
    PendingFile(name, mime, bytes)
}

@Composable
private fun SuggestRow(icon: Int, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = TextDim, modifier = Modifier.size(22.dp))
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}
