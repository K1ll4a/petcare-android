package com.petcare.app.presentation.account

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.petcare.app.domain.model.SpaceRole

@Composable
fun AuthScreen(state: AccountUiState, controller: AccountController) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Пароль не сохраняется в SavedState/Bundle.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    com.petcare.app.platform.PetCareBackHandler(enabled = register) { register = false; password = ""; confirmation = ""; localError = null; controller.clearMessage() }
    AccountPage(if (register) "Создать аккаунт" else "Добро пожаловать") {
        Text("PetCare", style = MaterialTheme.typography.headlineLarge)
        Text("Уход за питомцами вместе с близкими")
        Text("Учебная демонстрация · данные на этом устройстве", style = MaterialTheme.typography.labelMedium)
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Имя") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, supportingText = { if (register) Text("Не менее 8 символов") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (register) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OperationMessage(state, controller, showRetry = false)
        Button(onClick = {
            localError = null
            if (email.isBlank() || password.isBlank() || (register && name.isBlank())) localError = "Заполните все поля."
            else if (register && password != confirmation) localError = "Пароли не совпадают."
            else if (register) controller.register(name, email, password) else controller.login(email, password)
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Зарегистрироваться" else "Войти") }
        TextButton(onClick = { register = !register; password = ""; confirmation = ""; localError = null; controller.clearMessage() }, enabled = !state.busy) {
            Text(if (register) "Уже есть аккаунт? Войти" else "Создать аккаунт")
        }
        if (!register) OutlinedButton(onClick = { email = "grisha@example.com"; password = "demo12345" }, enabled = !state.busy) { Text("Заполнить демо-аккаунт") }
    }
}

private enum class SpaceForm { LIST, CREATE, JOIN }

@Composable
fun SpacesScreen(state: AccountUiState, controller: AccountController, onLogout: () -> Unit) {
    var form by rememberSaveable { mutableStateOf(SpaceForm.LIST) }
    var name by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("Europe/Moscow") }
    var code by rememberSaveable { mutableStateOf("") }
    // После подтверждённого изменения возвращаемся к обновлённому списку.
    LaunchedEffect(state.notice) { if (state.notice != null) { form = SpaceForm.LIST; name = ""; code = "" } }
    com.petcare.app.platform.PetCareBackHandler(enabled = form != SpaceForm.LIST) { form = SpaceForm.LIST; controller.clearMessage() }
    AccountPage(when (form) { SpaceForm.LIST -> "Мои пространства"; SpaceForm.CREATE -> "Новое пространство"; SpaceForm.JOIN -> "Присоединиться по коду" }) {
        Text("${state.session?.name} · ${state.session?.email}", style = MaterialTheme.typography.bodyMedium)
        OperationMessage(state, controller)
        when (form) {
            SpaceForm.LIST -> {
                if (state.spaces.isEmpty() && !state.busy) Text("У вас пока нет пространств. Создайте первое или попросите у владельца код приглашения.")
                state.spaces.forEach { space ->
                    OutlinedCard(onClick = { controller.selectSpace(space.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(space.name, style = MaterialTheme.typography.titleLarge)
                            Text(if (space.role == SpaceRole.OWNER) "Владелец" else "Участник")
                            Text(space.timezoneId, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(onClick = { form = SpaceForm.CREATE; controller.clearMessage() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Создать пространство") }
                OutlinedButton(onClick = { form = SpaceForm.JOIN; controller.clearMessage() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Присоединиться по коду") }
                TextButton(onClick = controller::loadSpaces, enabled = !state.busy) { Text("Обновить список") }
            }
            SpaceForm.CREATE -> {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(zone, { zone = it }, label = { Text("Часовой пояс") }, supportingText = { Text("Например Europe/Moscow или Asia/Yekaterinburg") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Button(onClick = { controller.createSpace(name, zone) }, enabled = !state.busy) { Text("Создать") }
            }
            SpaceForm.JOIN -> {
                Text("Код одноразовый и действует 24 часа. В демонстрации используйте код другого аккаунта на этом устройстве.")
                OutlinedTextField(code, { code = it }, label = { Text("Код приглашения") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Button(onClick = { controller.joinSpace(code) }, enabled = !state.busy && code.isNotBlank()) { Text("Присоединиться") }
            }
        }
        if (form != SpaceForm.LIST) TextButton(onClick = { form = SpaceForm.LIST; controller.clearMessage() }, enabled = !state.busy) { Text("К списку пространств") }
        TextButton(onClick = onLogout) { Text("Выйти из аккаунта") }
    }
}

@Composable
fun InvitationsScreen(state: AccountUiState, spaceId: Long, controller: AccountController, onBack: () -> Unit) {
    LaunchedEffect(spaceId) { controller.loadInvitations(spaceId) }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1_000); now = Clock.System.now() } }
    AccountPage("Приглашения") {
        Text("Каждый код подходит одному новому участнику и действует 24 часа.")
        OperationMessage(state, controller)
        Button(onClick = { controller.createInvitation(spaceId) }, enabled = !state.busy) { Text("Создать приглашение") }
        if (state.invitations.isEmpty() && !state.busy) Text("Приглашений пока нет")
        val zone = remember { TimeZone.currentSystemDefault() }
        state.invitations.asReversed().forEach { invite ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer { Text(invite.code, style = MaterialTheme.typography.titleLarge) }
                    Text(when { invite.used -> "Использовано"; invite.revoked -> "Отозвано"; !invite.isActive(now) -> "Срок истёк"; else -> "Действует до ${invite.expiresAt.displayDateTime(zone)} (время устройства)" })
                    if (invite.isActive(now)) TextButton(onClick = { controller.revokeInvitation(spaceId, invite.code) }, enabled = !state.busy) { Text("Отозвать") }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
private fun OperationMessage(state: AccountUiState, controller: AccountController, showRetry: Boolean = true) {
    if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Подождите…") }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        if (showRetry) TextButton(onClick = controller::retry, enabled = !state.busy) { Text("Повторить") }
    }
    state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
