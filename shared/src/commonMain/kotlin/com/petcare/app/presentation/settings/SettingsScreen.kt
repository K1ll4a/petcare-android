package com.petcare.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.presentation.account.AccountController
import com.petcare.app.presentation.account.AccountPage
import com.petcare.app.presentation.account.AccountUiState
import com.petcare.app.presentation.account.OperationMessage
import com.petcare.app.time.displayDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.TimeZone

/** Действие, которое нужно подтвердить. */
private sealed interface Confirm {
    data class Remove(val member: Member) : Confirm
    data class Transfer(val member: Member) : Confirm
    data object Leave : Confirm
    data object Unlink : Confirm
}

/**
 * «Настройки» выбранного пространства (ТЗ §8): состав группы, приглашения, выход, передача
 * владения и Telegram. Действия, недоступные роли, не показываются (FR-22, FR-23).
 */
@Composable
fun SettingsScreen(
    account: AccountUiState,
    controller: AccountController,
    spaceId: Long,
    telegram: TelegramController,
    onInvitations: () -> Unit,
    onBack: () -> Unit,
) {
    // Обновление: при открытии экрана и при возвращении приложения на передний план (FR-19).
    LaunchedEffect(spaceId) {
        controller.loadMembers(spaceId)
        telegram.load()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, spaceId) {
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumed) { controller.loadMembers(spaceId); telegram.load() }
                resumed = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val me = account.session?.userId
    val space = account.spaces.firstOrNull { it.id == spaceId }
    val isOwner = space?.role == SpaceRole.OWNER
    val others = account.members.filter { it.id != me }

    AccountPage("Настройки") {
        Text(space?.name ?: "Пространство", style = MaterialTheme.typography.titleLarge)
        Text(if (isOwner) "Ваша роль: владелец" else "Ваша роль: участник")
        OperationMessage(account, controller)

        // ---------------- Группа ----------------
        Text("Участники", style = MaterialTheme.typography.titleMedium)
        if (account.members.isEmpty() && !account.busy) Text("Список участников не загружен. Нажмите «Обновить».")
        account.members.forEach { member ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(member.name + if (member.id == me) " (вы)" else "", style = MaterialTheme.typography.titleMedium)
                    Text(if (member.role == SpaceRole.OWNER) "Владелец" else "Участник")
                    // Управление составом — только владельцу и только для других участников.
                    if (isOwner && member.id != me) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirm = Confirm.Transfer(member) }, enabled = !account.busy) { Text("Передать владение") }
                        TextButton(onClick = { confirm = Confirm.Remove(member) }, enabled = !account.busy) { Text("Удалить") }
                    }
                }
            }
        }
        if (isOwner) OutlinedButton(onClick = onInvitations, enabled = !account.busy, modifier = Modifier.fillMaxWidth()) { Text("Приглашения") }

        // Владелец не выходит без передачи владения (FR-23).
        when {
            !isOwner -> OutlinedButton(onClick = { confirm = Confirm.Leave }, enabled = !account.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Выйти из пространства")
            }
            others.isNotEmpty() -> Text("Чтобы выйти из пространства, сначала передайте владение другому участнику.", style = MaterialTheme.typography.bodySmall)
            else -> Text("Вы единственный участник. Чтобы выйти, пригласите нового участника и передайте ему владение.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { controller.loadMembers(spaceId); telegram.load() }, enabled = !account.busy) { Text("Обновить") }

        HorizontalDivider()
        TelegramSection(telegram, onUnlink = { confirm = Confirm.Unlink })
        HorizontalDivider()
        TextButton(onClick = onBack) { Text("К задачам") }
    }

    confirm?.let { action ->
        val (title, text, button) = when (action) {
            is Confirm.Remove -> Triple(
                "Удалить ${action.member.name}?",
                "Участник потеряет доступ к пространству. Его выполненные действия останутся в истории, незавершённые задачи перейдут вам.",
                "Удалить",
            )
            is Confirm.Transfer -> Triple(
                "Передать владение ${action.member.name}?",
                "Вы станете участником: управлять составом и приглашениями будет новый владелец.",
                "Передать",
            )
            Confirm.Leave -> Triple(
                "Выйти из пространства?",
                "Данные пространства исчезнут с этого устройства. Ваши выполненные действия останутся в истории, незавершённые задачи перейдут владельцу.",
                "Выйти",
            )
            Confirm.Unlink -> Triple("Отвязать Telegram?", "Напоминания перестанут приходить. Для новой привязки понадобится новая ссылка.", "Отвязать")
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        is Confirm.Remove -> controller.removeMember(spaceId, action.member)
                        is Confirm.Transfer -> controller.transferOwnership(spaceId, action.member)
                        Confirm.Leave -> controller.leaveSpace(spaceId)
                        Confirm.Unlink -> telegram.unlink()
                    }
                }) { Text(button) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun TelegramSection(telegram: TelegramController, onUnlink: () -> Unit) {
    val t by telegram.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Пока открыта ссылка, проверяем подтверждение каждые 3 секунды.
    LaunchedEffect(t.waitingForBot) {
        while (isActive && t.waitingForBot) {
            delay(3_000)
            telegram.pollConfirmation()
        }
    }

    Text("Telegram-напоминания", style = MaterialTheme.typography.titleMedium)
    if (t.loading) Text("Загружаем состояние…")
    if (t.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Ждём ответ сервера…")
    }
    t.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = telegram::load, enabled = !t.busy) { Text("Повторить") }
    }
    t.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    val status = t.status
    when {
        status == null -> Unit
        status.isConfirmed -> {
            Text("Подключён")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Напоминания о задачах", Modifier.weight(1f))
                // Положение переключателя — только из ответа сервера.
                Switch(checked = status.notificationsEnabled, onCheckedChange = telegram::setNotifications, enabled = !t.busy)
            }
            TextButton(onClick = onUnlink, enabled = !t.busy) { Text("Отвязать Telegram") }
        }
        t.waitingForBot -> t.link?.let { link ->
            Text("Не подтверждено: откройте ссылку и нажмите Start в боте. Экран обновится сам.")
            SelectionContainer { Text(link.url, style = MaterialTheme.typography.bodySmall) }
            link.expiresAt?.let { Text("Ссылка действует до ${it.displayDateTime(TimeZone.currentSystemDefault())} (время устройства)", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { runCatching { uriHandler.openUri(link.url) } }, enabled = !t.busy, modifier = Modifier.fillMaxWidth()) { Text("Открыть в Telegram") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = telegram::load, enabled = !t.busy) { Text("Проверить") }
                TextButton(onClick = telegram::createLink, enabled = !t.busy) { Text("Новая ссылка") }
            }
            if (telegram.demoBot != null) OutlinedButton(onClick = telegram::simulateBotStart, enabled = !t.busy) {
                Text("Демо: нажать Start в боте")
            }
        }
        else -> {
            Text("Не подключён")
            Button(onClick = telegram::createLink, enabled = !t.busy, modifier = Modifier.fillMaxWidth()) { Text("Подключить Telegram") }
        }
    }
}
