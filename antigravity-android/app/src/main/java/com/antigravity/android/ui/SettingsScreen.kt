package com.antigravity.android.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
    val name = email.substringBefore("@").ifBlank { "Google" }
    val initials = name.take(2).uppercase()
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
            Icon(painterResource(R.drawable.ds_back), contentDescription = "Назад", tint = TextMain, modifier = Modifier.size(22.dp))
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
            SettingsRow(painterResource(R.drawable.ds_person), "Персонализация") { page = "Персонализация" }
            SettingsRow(painterResource(R.drawable.ds_book), "Память") { page = "Память" }
            SettingsRow(painterResource(R.drawable.ds_plugin), "Плагины") { page = "Плагины" }
        }
        SectionLabel("Учетная запись")
        Group {
            SettingsRow(painterResource(R.drawable.ds_briefcase), "Рабочая область", "Личное") { page = "Рабочая область" }
            SettingsRow(painterResource(R.drawable.ds_sparkle), "Изменить план", tint = Blue) { page = "Изменить план" }
            SettingsRow(painterResource(R.drawable.ds_sliders), "Использование и лимиты") { page = "Использование и лимиты" }
            SettingsRow(painterResource(R.drawable.ds_heart), "Родительский контроль") { page = "Родительский контроль" }
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
                Icon(painterResource(R.drawable.ds_back), contentDescription = "Назад", tint = TextMain, modifier = Modifier.size(22.dp))
            }
            Text(title, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (title) {
                "Использование и лимиты" -> models.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable { onModel(item) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (item == model) Text("✓", color = Blue, fontSize = 16.sp)
                    }
                }
                "Внешний вид" -> Text("Система (по умолчанию)", color = TextMain, fontSize = 16.sp)
                "Акцентный цвет" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                    Text("Синий", color = TextMain, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                "Электронная почта" -> Text(email.ifBlank { "—" }, color = TextMain, fontSize = 16.sp)
                "Голос" -> Text("Разрешите доступ к микрофону в системных настройках, чтобы использовать Голос.", color = TextDim, fontSize = 16.sp, lineHeight = 22.sp)
                "Управление данными" -> Text("Чтобы экспортировать свои данные, откройте ChatGPT в браузере. Там вы сможете запросить и скачать свои данные.", color = TextDim, fontSize = 16.sp, lineHeight = 22.sp)
                "Выйти" -> Text("Выйти", color = Logout, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onLogout))
                else -> Text(title, color = TextDim, fontSize = 16.sp)
            }
        }
    }
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
