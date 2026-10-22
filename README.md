# PetCare — Android (неделя 1)

Kotlin + Jetpack Compose, minSdk 26.

```
app/src/main/java/com/petcare/app/
├── domain/model        Pet, Task, TaskItem, TaskStatus, Space
├── domain/repository   интерфейсы SpaceRepository, PetRepository, TaskRepository
├── data/remote/dto     DTO в формате Backend
├── data/mapper         DTO → доменные модели
├── data/fake           тестовые данные и фейковый репозиторий
├── presentation/home   временный главный экран (Controller, ViewModel, Compose)
├── ui/theme            временная тема
├── AppContainer.kt     ручной DI — здесь выбирается фейк или API
└── MainActivity.kt
app/src/main/assets/test_data.json   тестовые данные
```

Запуск тестов: `./gradlew :app:testDebugUnitTest`.

Если в общем проекте уже есть каркас от Mobile №1 — переносить пакеты `domain`, `data`, `presentation/home`, файл `assets/test_data.json` и тесты; `MainActivity`, тему и Gradle-файлы брать из общего проекта.
