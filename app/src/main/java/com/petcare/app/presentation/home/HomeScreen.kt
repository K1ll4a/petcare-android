package com.petcare.app.presentation.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.TaskStatus
import com.petcare.app.ui.theme.PetCareTheme

/**
 * ВРЕМЕННЫЙ главный экран (неделя 1): выбор питомца и список его задач со статусами.
 * Финальный вид будет по макетам дизайнера.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onSelectPet = viewModel::selectPet,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onSelectPet: (Long) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    val title = (state as? HomeUiState.Ready)?.spaceName?.let { "PetCare · $it" } ?: "PetCare"
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (state) {
                HomeUiState.Loading -> CenteredLoading()
                is HomeUiState.Error -> ErrorBlock(state.message, onRetry)
                is HomeUiState.Ready -> ReadyContent(state, onSelectPet, onRetry, onRefresh)
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
) {
    Column(Modifier.fillMaxSize()) {
        // Выбор питомца доступен даже во время загрузки задач (NFR-04).
        if (state.pets.isNotEmpty()) {
            PetSelector(state.pets, state.selectedPetId, onSelectPet)
        }
        state.selectedPet?.let { PetInfo(it) }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val tasks = state.tasks) {
                TasksState.Loading -> CenteredLoading()
                TasksState.NoPets -> MessageBlock("В пространстве пока нет питомцев")
                TasksState.Empty -> MessageBlock("У питомца пока нет задач")
                is TasksState.Error -> ErrorBlock(tasks.message, onRetry)
                is TasksState.Content -> TaskList(tasks.items)
            }
        }
    }
}

@Composable
private fun PetSelector(pets: List<Pet>, selectedId: Long?, onSelect: (Long) -> Unit) {
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
    }
}

@Composable
private fun PetInfo(pet: Pet) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "${pet.name} · ${pet.typeName ?: "вид не указан"}",
            style = MaterialTheme.typography.titleMedium,
        )
        // Особенности питомца важны при уходе — показываем сразу (FR-04).
        pet.allergies?.let { Text("Аллергии: $it", style = MaterialTheme.typography.bodySmall) }
        pet.intolerances?.let { Text("Непереносимость: $it", style = MaterialTheme.typography.bodySmall) }
        pet.features?.let { Text("Особенности: $it", style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TaskList(items: List<TaskItemUi>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { TaskCard(it) }
    }
}

@Composable
private fun TaskCard(item: TaskItemUi) {
    val inactive = item.status == TaskStatus.CANCELLED || item.status == TaskStatus.MISSED
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = item.dueText + if (item.isDaily) " · ежедневно" else " · разово",
                style = MaterialTheme.typography.bodyMedium,
                color = if (inactive) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            Text(item.assigneeText, style = MaterialTheme.typography.bodySmall)
            item.completedText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (item.isScheduleDisabled) {
                Text(
                    "Расписание отключено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageBlock(text: String) {
    // LazyColumn, чтобы pull-to-refresh работал и в пустом состоянии.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    PetCareTheme {
        HomeContent(
            state = HomeUiState.Ready(
                spaceName = "Дом",
                pets = listOf(
                    Pet(10, 1, "Tom", 1, "кот", allergies = "курица"),
                    Pet(11, 1, "Рекс", 2, "собака"),
                ),
                selectedPetId = 10,
                tasks = TasksState.Content(
                    listOf(
                        TaskItemUi("3", "Дать таблетку", "сегодня, 12:00", "Ответственный: Аня",
                            TaskStatus.OVERDUE, "Просрочено", false, false, null),
                        TaskItemUi("2_x", "Расчесать", "сегодня, 21:00", "Ответственный: Гриша",
                            TaskStatus.UPCOMING, "Предстоит", true, false, null),
                        TaskItemUi("1_x", "Покормить", "сегодня, 08:00", "Ответственный: Аня",
                            TaskStatus.DONE, "Выполнено", true, false, "Выполнил(а): Гриша в 08:10"),
                    ),
                ),
            ),
            onSelectPet = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}
