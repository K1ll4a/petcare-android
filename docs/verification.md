# Проверка общего Android-проекта

Дата: 02.11.2026 (Europe/Moscow). Ветка: `feature/shared-auth-spaces`.
Среда сборки: macOS ARM64, OpenJDK 17.0.13, Gradle Wrapper 8.11.1, AGP 8.7.3,
SDK Platform 35, Build-Tools 34.0.0. SDK для проверки установлен во временную папку `/private/tmp/petcare-android-sdk`.

## Сборка и автоматические проверки

Выполнено:

```sh
ANDROID_HOME=/private/tmp/petcare-android-sdk ./gradlew \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --no-daemon
```

Результат: **BUILD SUCCESSFUL**, debug APK и APK UI-тестов собраны, **99 unit-тестов прошли**, 0 ошибок/падений/пропусков.
Lint завершён без ошибок; есть предупреждения о более новых версиях зависимостей и оформлении конфигурации.
Версии основного стека сохранены из общего проекта.

| Класс | Успешно |
|---|---:|
| AccountRepositoryTest | 14 |
| AccountControllerTest | 3 |
| ApiTest | 14 |
| EditControllerTest | 13 |
| FakeRepositoryTest | 15 |
| HomeControllerTest | 17 |
| MapperTest | 17 |
| TaskPresentationTest | 6 |
| **Всего** | **99** |

Новые тесты проверяют регистрацию и повторный вход, хеширование сохранённого пароля, валидацию,
отдельные роли в двух пространствах, создание пространства и работу с его питомцами/задачами,
одноразовость и срок приглашения на границе 24 часов, отзыв, запрет управления кодами участнику,
отсутствие повторного членства, выход во время запроса, запрет защищённого чтения после выхода,
отбрасывание результата предыдущего аккаунта и понятную ошибку с повтором.
Тесты Mobile №2 проверяют также поздние ответы при переключении пространства и питомца.

## Проверка на Android

Эмулятор: **PetCare_Pixel7_API35**, профиль **Pixel 7**, Android 15 / API 35,
официальный образ **AOSP ATD ARM64 revision 1**, 1080×2400, аппаратное ускорение Hypervisor.Framework.

`:app:connectedDebugAndroidTest` — **2 теста прошли**, 0 ошибок/пропусков:

1. Вход Гришей → «Семья» → профиль Tom → создание задачи → список пространств → «Дача»
   с Муркой, без данных Tom и без управления приглашениями → выход → повторное создание Activity без защищённого экрана.
2. Регистрация нового аккаунта → создание пространства → пустой список питомцев → создание и отзыв приглашения.

Первый прогон выявил сброс выбранного пространства при начальном ON_RESUME. Исправление сохраняет
запрошенное пространство при повторной загрузке и пропускает лишнее первое обновление.
Добавлен регрессионный unit-тест `refreshDuringInitialLoad_keepsRequestedSpace`; оба UI-сценария
после исправления прошли вместе с полным набором unit-тестов и Lint.

После тестов APK дополнительно установлен обычной командой `adb install -r`; холодный запуск
через `am start -W` вернул `Status: ok`, Activity `com.petcare.app/.MainActivity`.
Проверки Compose находятся в `app/src/androidTest/java/com/petcare/app/NavigationTest.kt`.
Отчёт на устройстве: `app/build/reports/androidTests/connected/debug/index.html`.
После включения отрисовки в ATD (`debug.hwui.drawing_enabled=1`) визуально проверены
[вход](screenshots/login.png) и [главный экран](screenshots/home.png).

## Публикация

Исходники: [feature/shared-auth-spaces](https://github.com/K1ll4a/petcare-android/tree/feature/shared-auth-spaces).
[GitHub Actions](https://github.com/K1ll4a/petcare-android/actions/workflows/android.yml) собирает APK,
проверяет unit-тесты и Lint, сохраняет `petcare-debug` и `android-reports`.
Исправленная конфигурация CI проверена успешным [запуском](https://github.com/K1ll4a/petcare-android/actions/runs/41459553997).
После финального коммита запускается новая сборка; UI-тесты выполнялись локально на указанном эмуляторе.

## Ограничения проверки

Реального Backend и второго телефона в этом прогоне нет. Сценарии аккаунтов/приглашений локальные.
Отдельная проверка Android 8.0 / API 26 не проводилась. Независимый запуск Mobile №2 ещё не подтверждён.

Локальный APK проверенной сборки: `app/build/outputs/apk/debug/app-debug.apk` (10,671,761 байт).
SHA-256: `dc9e00d43a4ec2fea22b2c6558374d342d462bfe63147cb8a452dbe59f11ca5e`.
