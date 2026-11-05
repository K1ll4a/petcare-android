package com.petcare.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.domain.model.TaskStatus

/** Действия главного экрана, которые ведут на другие экраны. */
data class HomeNavigation(
    val onAddPet: () -> Unit,
    val onEditPet: (Pet) -> Unit,
    val onAddTask: () -> Unit,
    val onEditTask: (key: String) -> Unit,
    val onSelectSpace: ((Long) -> Unit)? = null,
)

@Composable
fun HomeScreen(viewModel: HomeViewModel, nav: HomeNavigation) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onSelectSpace = nav.onSelectSpace ?: viewModel::selectSpace,
        onSelectPet = viewModel::selectPet,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onSelectSpace: (Long) -> Unit,
    onSelectPet: (Long) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    nav: HomeNavigation,
) {
    val ready = state as? HomeUiState.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PetCare")
                        ready?.let {
                            Text(
                                text = "Время задач: ${it.space.timezoneLabel()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (it.space.isTimezoneValid) Color.Unspecified else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                actions = {
                    ready?.let { SpaceMenu(it.spaces, it.space, onSelectSpace) }
                    // Ручное обновление — профиль и задачи (шаг 4). Также свайп вниз.
                    TextButton(onClick = onRefresh, enabled = ready?.isRefreshing == false) { Text("Обновить") }
                },
            )
        },
        floatingActionButton = {
            if (ready?.selectedPetId != null && ready.space.isTimezoneValid) {
                ExtendedFloatingActionButton(onClick = nav.onAddTask) { Text("+ Задача") }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (state) {
                HomeUiState.Loading -> CenteredLoading()
                is HomeUiState.Error -> ErrorBlock(state.message, onRetry)
                is HomeUiState.NoSpaces -> MessageBlock(
                    state.message ?: "Вы пока не состоите ни в одном пространстве. Создайте его или присоединитесь по коду.",
                )
                is HomeUiState.Ready -> ReadyContent(state, onSelectPet, onRetry, onRefresh, nav)
            }
        }
    }
}

/** Выбор пространства. При переключении старые данные сразу убираются (FR-21). */
@Composable
private fun SpaceMenu(spaces: List<Space>, current: Space, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(current.name + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            spaces.forEach { space ->
                DropdownMenuItem(
                    text = {
                        val role = if (space.role == SpaceRole.OWNER) "владелец" else "участник"
                        Text("${space.name} · $role")
                    },
                    onClick = {
                        open = false
                        onSelect(space.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: HomeUiState.Ready,
    onSelectPet: (Long) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    nav: HomeNavigation,
) {
    Column(Modifier.fillMaxSize()) {
        state.staleMessage?.let { Banner(it) }

        // Выбор питомца доступен и во время загрузки (NFR-04).
        when (val pets = state.pets) {
            PetsState.Loading -> Text("Загружаем питомцев…", Modifier.padding(16.dp))
            is PetsState.Error -> ErrorBlock(pets.message, onRetry)
            is PetsState.Content -> PetSelector(pets.items, state.selectedPetId, onSelectPet, nav.onAddPet)
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "profile") { ProfileBlock(state.profile, onRetry, nav.onEditPet) }
                when (val tasks = state.tasks) {
                    TasksState.Loading -> item(key = "tasks-loading") { InlineLoading("Загружаем задачи…") }
                    TasksState.NoPets -> item(key = "no-pets") {
                        MessageText("В пространстве пока нет питомцев. Нажмите «+ Питомец».")
                    }
                    TasksState.Empty -> item(key = "empty") { MessageText("У питомца пока нет задач") }
                    is TasksState.Error -> item(key = "tasks-error") { InlineError(tasks.message, onRetry) }
                    is TasksState.Content -> {
                        item(key = "tz") {
                            Text(
                                "Сроки — по поясу пространства: ${tasks.timezoneLabel}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        items(tasks.items, key = { it.key }) { item ->
                            TaskCard(item, onClick = { nav.onEditTask(item.key) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetSelector(pets: List<Pet>, selectedId: Long?, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pets, key = { it.id }) { pet ->
            FilterChip(
                selected = pet.id == selectedId,
                onClick = { onSelect(pet.id) },
                label = { Text(pet.name) },
            )
        }
        item(key = "add") { AssistChip(onClick = onAdd, label = { Text("+ Питомец") }) }
    }
}

/** Профиль питомца: аллергии и непереносимости — отдельными строками. */
@Composable
private fun ProfileBlock(profile: ProfileState, onRetry: () -> Unit, onEdit: (Pet) -> Unit) {
    when (profile) {
        ProfileState.None -> Unit
        ProfileState.Loading -> InlineLoading("Загружаем профиль…")
        is ProfileState.Error -> InlineError(profile.message, onRetry)
        is ProfileState.Content -> {
            val pet = profile.pet
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pet.name} · ${pet.typeName ?: "вид не указан"}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onEdit(pet) }) { Text("Профиль") }
                    }
                    ProfileLine("Аллергии", pet.allergies)
                    ProfileLine("Непереносимости", pet.intolerances)
                    ProfileLine("Особенности", pet.features)
                    ProfileLine("Заметки по уходу", pet.careNotes)
                }
            }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String?) {
    Text("$label: ${value ?: "не указано"}", style = MaterialTheme.typography.bodySmall)
}

/** Карточка задачи: срок с поясом пространства, ответственный и состояние. */
@Composable
private fun TaskCard(item: TaskItemUi, onClick: () -> Unit) {
    val inactive = item.status == TaskStatus.CANCELLED || item.status == TaskStatus.MISSED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isEditable, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (item.status == TaskStatus.OVERDUE) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(item.status, item.statusText)
            }
            Text(
                text = "Срок: " + item.dueText + if (item.isDaily) " · ежедневно" else " · разово",
                style = MaterialTheme.typography.bodyMedium,
                color = if (inactive) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            Text("Пояс: ${item.zoneText}", style = MaterialTheme.typography.bodySmall)
            Text(item.assigneeText, style = MaterialTheme.typography.bodySmall)
            item.completedText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (item.isScheduleDisabled) {
                Text("Расписание отключено", style = MaterialTheme.typography.bodySmall)
            }
            if (item.isEditable) {
                Text("Нажмите, чтобы изменить", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TaskStatus, text: String) {
    val (bg, fg) = when (status) {
        TaskStatus.OVERDUE -> Color(0xFFC62828) to Color.White
        TaskStatus.UPCOMING -> Color(0xFF1565C0) to Color.White
        TaskStatus.DONE -> Color(0xFF2E7D32) to Color.White
        TaskStatus.MISSED -> Color(0xFF757575) to Color.White
        TaskStatus.CANCELLED -> Color(0xFFBDBDBD) to Color.Black
        TaskStatus.UNKNOWN -> Color(0xFFFFE082) to Color.Black
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun Banner(text: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun InlineLoading(text: String) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(end = 12.dp))
        Text(text)
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun MessageText(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
}

@Composable
private fun MessageBlock(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}
