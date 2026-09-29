package com.antigravity.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.android.R

@Composable
fun SettingsScreen(email: String, onBack: () -> Unit, onLogout: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("root") }
    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ds_back),
                contentDescription = "Назад",
                tint = TextMain,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { if (page == "root") onBack() else page = "root" }
                    .padding(12.dp),
            )
            Text(
                titleFor(page),
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (page) {
                "root" -> RootSettings(email) { page = it }
                "account" -> AccountSettings(email, onLogout)
                "voice" -> Body(
                    "Разрешите доступ к микрофону в системных настройках, чтобы использовать Голос.",
                )
                "language" -> {
                    CheckRow("Русский", true)
                    CheckRow("Система (по умолчанию)", false)
                }
                "theme" -> CheckRow("Система (по умолчанию)", true)
                "data" -> Body("Чтобы экспортировать свои данные, откройте ChatGPT в браузере. Там вы сможете запросить и скачать свои данные.")
                "archive" -> Body("Архивированные чаты доступны в настройках.")
            }
        }
    }
}

@Composable
private fun RootSettings(email: String, open: (String) -> Unit) {
    SettingsRow(painterResource(R.drawable.ds_person), email.ifBlank { "Google" }, null) { open("account") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_sliders), "Настройки учетной записи", null) { open("account") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_personalization), "Настройки голоса", null) { open("voice") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_globe), "Язык приложения", "Русский") { open("language") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_moon), "Система (по умолчанию)", null) { open("theme") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_archive), "История и данные", null) { open("data") }
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_archive), "Архивированные чаты", null) { open("archive") }
}

@Composable
private fun AccountSettings(email: String, onLogout: () -> Unit) {
    Body(email.ifBlank { "Google" })
    SettingsRow(painterResource(R.drawable.ds_person), "Выйти из системы", null, onLogout)
    Hairline()
    SettingsRow(painterResource(R.drawable.ds_person), "Выйти на всех устройствах", null, onLogout)
}

@Composable
private fun SettingsRow(icon: Painter, title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextMain, modifier = Modifier.size(22.dp))
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        if (value != null) {
            Text(value, color = TextDim, fontSize = 16.sp)
        }
        Icon(
            painterResource(R.drawable.ds_chevron_right),
            contentDescription = null,
            tint = TextDim,
            modifier = Modifier.padding(start = 8.dp).size(16.dp),
        )
    }
}

@Composable
private fun CheckRow(title: String, selected: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = TextMain, fontSize = 16.sp)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = TextDim, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(20.dp))
}

@Composable
private fun Hairline() {
    Spacer(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.5.dp).background(Hairline))
}

private fun titleFor(page: String): String = when (page) {
    "account" -> "Настройки учетной записи"
    "voice" -> "Настройки голоса"
    "language" -> "Язык приложения"
    "theme" -> "Система (по умолчанию)"
    "data" -> "История и данные"
    "archive" -> "Архивированные чаты"
    else -> "Настройки"
}
