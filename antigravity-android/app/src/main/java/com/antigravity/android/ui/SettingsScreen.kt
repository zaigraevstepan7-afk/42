package com.antigravity.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.android.R

@Composable
fun SettingsScreen(
    email: String,
    displayName: String = "",
    onBack: () -> Unit,
    onLogout: () -> Unit,
    models: List<String> = emptyList(),
    model: String = "",
    onModel: (String) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    if (page != null) {
        DetailPage(page!!, email, models, model, onModel, onLogout) { page = null }
        return
    }
    val name = displayName.ifBlank { email.substringBefore("@").ifBlank { "Google" } }
    val initials = profileInitials(displayName, email)
    Column(
        Modifier
            .fillMaxSize()
            .background(Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(40.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(White)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ds_arrow_left), contentDescription = "Назад", tint = TextMain, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Avatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, color = White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .size(26.dp)
                        .shadow(1.dp, CircleShape)
                        .clip(CircleShape)
                        .background(White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ds_pencil), contentDescription = null, tint = TextMain, modifier = Modifier.size(14.dp))
                }
            }
            Text(name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
        }
        SectionLabel("Мой ChatGPT")
        Group {
            SettingsRow(painterResource(R.drawable.ds_face), "Персонализация") { page = "Персонализация" }
            SettingsRow(painterResource(R.drawable.ds_book_open), "Память") { page = "Память" }
            SettingsRow(painterResource(R.drawable.ds_apps), "Плагины") { page = "Плагины" }
        }
        SectionLabel("Учетная запись")
        Group {
            SettingsRow(painterResource(R.drawable.ds_briefcase), "Рабочая область", "Личное") { page = "Рабочая область" }
            SettingsRow(painterResource(R.drawable.ds_sparkle), "Изменить план", tint = Blue) { page = "Изменить план" }
            SettingsRow(painterResource(R.drawable.ds_chart), "Использование и лимиты") { page = "Использование и лимиты" }
            SettingsRow(painterResource(R.drawable.ds_parent), "Родительский контроль") { page = "Родительский контроль" }
            SettingsRow(painterResource(R.drawable.ds_mail), "Электронная почта", email.ifBlank { "—" }) { page = "Электронная почта" }
            SettingsRow(painterResource(R.drawable.ds_lock_person), "Проверка возраста") { page = "Проверка возраста" }
        }
        Spacer(Modifier.height(12.dp))
        Group {
            SettingsRow(painterResource(R.drawable.ds_sun), "Внешний вид", caret = true) { page = "Внешний вид" }
            SettingsRow(painterResource(R.drawable.ds_palette), "Акцентный цвет", "Синий", caret = true) { page = "Акцентный цвет" }
        }
        Spacer(Modifier.height(12.dp))
        Group {
            SettingsRow(painterResource(R.drawable.ds_gear), "Общие") { page = "Общие" }
            SettingsRow(painterResource(R.drawable.ds_bell), "Уведомления") { page = "Уведомления" }
            SettingsRow(painterResource(R.drawable.ds_voice), "Голос") { page = "Голос" }
            SettingsRow(painterResource(R.drawable.ds_shield), "Безопасность и благополучие") { page = "Безопасность и благополучие" }
            SettingsRow(painterResource(R.drawable.ds_lock_person), "Безопасность и вход") { page = "Безопасность и вход" }
            SettingsRow(painterResource(R.drawable.ds_remote), "Удаленное управление") { page = "Удаленное управление" }
            SettingsRow(painterResource(R.drawable.ds_storage), "Хранилище") { page = "Хранилище" }
            SettingsRow(painterResource(R.drawable.ds_shield), "Центр конфиденциальности") { page = "Центр конфиденциальности" }
            SettingsRow(painterResource(R.drawable.ds_storage), "Управление данными") { page = "Управление данными" }
            SettingsRow(painterResource(R.drawable.ds_megaphone), "Управление рекламой") { page = "Управление рекламой" }
            SettingsRow(painterResource(R.drawable.ds_bug), "Сообщить об ошибке") { page = "Сообщить об ошибке" }
            SettingsRow(painterResource(R.drawable.ds_info), "Информация") { page = "Информация" }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .clickable(onClick = onLogout)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ds_logout), contentDescription = null, tint = Logout, modifier = Modifier.size(22.dp))
            Text("Выйти", color = Logout, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 14.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Skeleton() {
    val alpha = rememberInfiniteTransition(label = "shimmer").animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column {
        repeat(5) {
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth(if (it % 2 == 0) 1f else 0.72f).height(18.dp).clip(RoundedCornerShape(8.dp)).background(Card.copy(alpha = alpha.value)))
        }
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Card.copy(alpha = alpha.value)))
    }
}

@Composable
private fun MemoryForm() {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Память", color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(Card))
    }
    listOf("Ваш никнейм", "Ваша профессия", "Подробнее о вас").forEach { hint ->
        var value by rememberSaveable(hint) { mutableStateOf("") }
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = TextStyle(color = TextMain, fontSize = 16.sp),
            cursorBrush = SolidColor(Blue),
            modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(White).padding(16.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = TextFaint, fontSize = 16.sp)
                inner()
            },
        )
    }
}

@Composable
private fun PluginsPage() {
    CircularProgressIndicator(color = TextMain, strokeWidth = 2.dp, modifier = Modifier.padding(vertical = 24.dp).size(28.dp))
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(Card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ds_grid), contentDescription = null, tint = TextMain, modifier = Modifier.size(18.dp))
        Text("Просмотреть плагины", color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PlusPage() {
    Text("ChatGPT Plus", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Больше сообщений и возможностей", color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Card).padding(4.dp)) {
        Text("Go", color = TextDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        Text("Plus", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(White).padding(horizontal = 18.dp, vertical = 8.dp))
    }
    Spacer(Modifier.height(16.dp))
    listOf("Доступ к GPT-5", "Расширенное рассуждение", "Больше сообщений и загрузок", "Расширенное создание изображений").forEach { feature ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(feature, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("—", color = TextFaint, fontSize = 16.sp, modifier = Modifier.padding(end = 18.dp))
            Text("✓", color = Blue, fontSize = 16.sp)
        }
    }
    Box(
        Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Black).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Перейти на план за 19,99 $", color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UsagePage(models: List<String>, model: String, onModel: (String) -> Unit) {
    Text("Лимит использования", color = TextMain, fontSize = 16.sp)
    Text("Осталось 100%", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Card)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Blue))
    }
    Text("Сброс в начале следующего периода", color = TextFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
    models.forEach { item ->
        Row(Modifier.fillMaxWidth().height(48.dp).clickable { onModel(item) }, verticalAlignment = Alignment.CenterVertically) {
            Text(item, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (item == model) Text("✓", color = Blue, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)) {
        content()
    }
}

@Composable
private fun DetailPage(
    title: String,
    email: String,
    models: List<String>,
    model: String,
    onModel: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Page).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(White).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ds_arrow_left), contentDescription = "Назад", tint = TextMain, modifier = Modifier.size(22.dp))
            }
            Text(title, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (title) {
                "Персонализация", "Уведомления" -> Skeleton()
                "Память" -> MemoryForm()
                "Плагины" -> PluginsPage()
                "Изменить план" -> PlusPage()
                "Использование и лимиты" -> UsagePage(models, model, onModel)
                "Внешний вид" -> Text("Система (по умолчанию)", color = TextMain, fontSize = 16.sp)
                "Акцентный цвет" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                    Text("Синий", color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                "Электронная почта" -> Text(email.ifBlank { "—" }, color = TextMain, fontSize = 16.sp)
                "Голос" -> Text("Разрешите доступ к микрофону в системных настройках, чтобы использовать Голос.", color = TextDim, fontSize = 16.sp, lineHeight = 22.sp)
                "Управление данными" -> Text("Чтобы экспортировать свои данные, откройте ChatGPT в браузере. Там вы сможете запросить и скачать свои данные.", color = TextDim, fontSize = 16.sp, lineHeight = 22.sp)
                else -> Text(title, color = TextDim, fontSize = 16.sp)
            }
        }
    }
}

private fun profileInitials(name: String, email: String): String {
    val parts = name.split(" ").filter { it.isNotBlank() }
    val latin = parts.mapNotNull { word -> word.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' } }
    if (latin.size >= 2) return "${latin[0]}${latin[1]}".uppercase()
    val fromEmail = email.substringBefore("@").filter { it.isLetter() }.take(2)
    if (fromEmail.length == 2) return fromEmail.uppercase()
    return name.take(1).uppercase().ifBlank { "G" }
}

@Composable
private fun SettingsRow(
    icon: Painter,
    title: String,
    value: String? = null,
    tint: androidx.compose.ui.graphics.Color = TextMain,
    caret: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = tint, fontSize = 16.sp)
            if (value != null && title == "Акцентный цвет") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                    Text(value, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            } else if (value != null) {
                Text(value, color = TextDim, fontSize = 13.sp)
            }
        }
        if (caret) {
            Icon(painterResource(R.drawable.ds_chevron), contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
        }
    }
}
