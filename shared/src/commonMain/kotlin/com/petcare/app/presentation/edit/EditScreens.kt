package com.petcare.app.presentation.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.TaskDraft

// ---------------- Питомец ----------------

@Composable
fun PetEditScreen(controller: PetEditController, onBack: () -> Unit) {
    val s by controller.state.collectAsStateWithLifecycle()
    FormScaffold(title = if (s.isNew) "Новый питомец" else "Профиль питомца", onBack = onBack) {
        Field("Имя *", s.draft.name, s.fieldErrors[PetDraft.FIELD_NAME]) { v -> controller.edit { it.copy(name = v) } }

        Text("Вид *", style = MaterialTheme.typography.labelLarge)
        s.typesError?.let { ErrorWithRetry(it, controller::loadTypes) }
        ChipRow {
            s.types.forEach { type ->
                FilterChip(
                    selected = s.draft.typeId == type.id,
                    onClick = { controller.edit { it.copy(typeId = type.id) } },
                    label = { Text(type.name) },
                )
            }
        }
        FieldError(s.fieldErrors[PetDraft.FIELD_TYPE])

        // Отдельные поля аллергий и непереносимостей (задача #14, шаг 3).
        Field("Аллергии", s.draft.allergies, s.fieldErrors[PetDraft.FIELD_ALLERGIES], multiline = true) { v ->
            controller.edit { it.copy(allergies = v) }
        }
        Field("Непереносимости", s.draft.intolerances, s.fieldErrors[PetDraft.FIELD_INTOLERANCES], multiline = true) { v ->
            controller.edit { it.copy(intolerances = v) }
        }
        Field("Особенности", s.draft.features, s.fieldErrors[PetDraft.FIELD_FEATURES], multiline = true) { v ->
            controller.edit { it.copy(features = v) }
        }
        Field("Заметки по уходу", s.draft.careNotes, s.fieldErrors[PetDraft.FIELD_CARE_NOTES], multiline = true) { v ->
            controller.edit { it.copy(careNotes = v) }
        }

        SaveBlock(s.save, enabled = s.save != SaveState.Saving, onSave = controller::save, onBack = onBack)
    }
}

// ---------------- Задача ----------------

@Composable
fun TaskEditScreen(controller: TaskEditController, onBack: () -> Unit) {
    val s by controller.state.collectAsStateWithLifecycle()
    FormScaffold(title = if (s.isNew) "Новая задача" else "Изменить задачу", onBack = onBack) {
        s.timezoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.loadError?.let { ErrorWithRetry(it, controller::loadChoices) }
        if (s.isLoading) Text("Загружаем питомцев и участников…")

        Field("Название *", s.title, s.fieldErrors[TaskDraft.FIELD_TITLE]) { v -> controller.edit { it.copy(title = v) } }

        Text("Питомец *", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            s.pets.forEach { pet ->
                FilterChip(
                    selected = s.petId == pet.id,
                    onClick = { controller.edit { it.copy(petId = pet.id) } },
                    label = { Text(pet.name) },
                )
            }
        }
        FieldError(s.fieldErrors[TaskDraft.FIELD_PET])

        Text("Ответственный *", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            s.members.forEach { m ->
                FilterChip(
                    selected = s.assigneeId == m.id,
                    onClick = { controller.edit { it.copy(assigneeId = m.id) } },
                    label = { Text(m.name) },
                )
            }
        }
        FieldError(s.fieldErrors[TaskDraft.FIELD_ASSIGNEE])

        Text("Повторение", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            FilterChip(
                selected = s.repeat == RepeatType.ONCE,
                onClick = { controller.edit { it.copy(repeat = RepeatType.ONCE) } },
                label = { Text("Однократно") },
            )
            FilterChip(
                selected = s.repeat == RepeatType.DAILY,
                onClick = { controller.edit { it.copy(repeat = RepeatType.DAILY) } },
                label = { Text("Ежедневно") },
            )
        }

        Text("Срок — по поясу пространства: ${s.timezoneLabel}", style = MaterialTheme.typography.labelMedium)
        Field(
            label = if (s.repeat == RepeatType.DAILY) "Дата начала (ДД.ММ.ГГГГ) *" else "Дата (ДД.ММ.ГГГГ) *",
            value = s.dateText,
            error = s.fieldErrors[TaskDraft.FIELD_DATE],
            keyboard = KeyboardType.Number,
        ) { v -> controller.edit { it.copy(dateText = v) } }
        Field(
            label = if (s.repeat == RepeatType.DAILY) "Время каждый день (ЧЧ:ММ) *" else "Время (ЧЧ:ММ) *",
            value = s.timeText,
            error = s.fieldErrors[TaskDraft.FIELD_TIME],
            keyboard = KeyboardType.Number,
        ) { v -> controller.edit { it.copy(timeText = v) } }

        SaveBlock(s.save, enabled = s.canSave, onSave = controller::save, onBack = onBack)
    }
}

// ---------------- Общие элементы форм ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Назад") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    multiline: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldError(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    // Временная раскладка (прокрутка вбок); окончательная — по макетам дизайнера.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}

/**
 * Кнопка и результат сохранения. «Сохранено» — только после ответа сервера;
 * пока ждём — индикатор и «Сохраняем…», кнопка недоступна.
 */
@Composable
private fun SaveBlock(save: SaveState, enabled: Boolean, onSave: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        if (save == SaveState.Saving) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text("Сохраняем… ждём ответ сервера")
        } else {
            Text("Сохранить")
        }
    }
    when (save) {
        SaveState.Idle, SaveState.Saving -> Unit
        is SaveState.Saved -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ${save.message}", color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("К списку") }
        }
        is SaveState.Failed -> Text(save.message, color = MaterialTheme.colorScheme.error)
        is SaveState.Unconfirmed -> Text(save.message, color = Color(0xFFE65100))
    }
}
