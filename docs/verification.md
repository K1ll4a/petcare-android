# Проверка общего Android-проекта

Дата: 02.11.2026 (Europe/Moscow). Ветка: `feature/shared-auth-spaces`.
Среда сборки: macOS ARM64, OpenJDK 17.0.13, Gradle Wrapper 8.11.1, AGP 8.7.3,
SDK Platform 35, Build-Tools 34.0.0. SDK для проверки установлен во временную папку `/private/tmp/petcare-android-sdk`.

## Сборка и автоматические проверки

Выполнено:

```sh
ANDROID_HOME=/private/tmp/petcare-android-sdk ./gradlew \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon
```

Результат: **BUILD SUCCESSFUL**, debug APK и APK UI-тестов собраны, **98 unit-тестов прошли**, 0 ошибок/падений/пропусков.
Lint завершён без ошибок; есть предупреждения о более новых версиях зависимостей и оформлении конфигурации.
Версии основного стека сохранены из общего проекта.

| Класс | Успешно |
|---|---:|
| AccountRepositoryTest | 14 |
| AccountControllerTest | 3 |
| ApiTest | 14 |
| EditControllerTest | 13 |
| FakeRepositoryTest | 15 |
| HomeControllerTest | 16 |
| MapperTest | 17 |
| TaskPresentationTest | 6 |
| **Всего** | **98** |

Новые тесты проверяют регистрацию и повторный вход, хеширование сохранённого пароля, валидацию,
отдельные роли в двух пространствах, создание пространства и работу с его питомцами/задачами,
одноразовость и срок приглашения на границе 24 часов, отзыв, запрет управления кодами участнику,
отсутствие повторного членства, выход во время запроса, запрет защищённого чтения после выхода,
отбрасывание результата предыдущего аккаунта и понятную ошибку с повтором.
Тесты Mobile №2 проверяют также поздние ответы при переключении пространства и питомца.

## Проверка на Android

Результат запуска на устройстве будет записан после выполнения `:app:connectedDebugAndroidTest`.
Проверки Compose находятся в `app/src/androidTest/java/com/petcare/app/NavigationTest.kt`.

## Ограничения проверки

Реального Backend и второго телефона в этом прогоне нет. Сценарии аккаунтов/приглашений локальные.
Отдельная проверка Android 8.0 / API 26 не проводилась. Независимый запуск Mobile №2 ещё не подтверждён.
