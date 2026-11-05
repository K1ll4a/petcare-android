# PetCare для Android и iOS

Общий учебный проект Mobile №1 и Mobile №2 на **Kotlin Multiplatform + Compose Multiplatform**.
Одинаковые экраны, навигация, модели и логика работают из модуля `shared`; Android и iOS имеют собственные точки запуска.

Репозиторий: [K1ll4a/petcare-android](https://github.com/K1ll4a/petcare-android).
Переход на две платформы: ветка `feature/android-ios`.

## Структура

```text
app/                           Android-приложение и его инструментальные тесты
shared/
  src/commonMain/kotlin/       общие экраны, контроллеры, модели, JSON, репозитории и HTTP
  src/androidMain/kotlin/      SharedPreferences, Android Back, Java crypto, HTTP OkHttp engine
  src/iosMain/kotlin/          NSUserDefaults, CommonCrypto/Security, HTTP Darwin engine, UIViewController
  src/commonTest/kotlin/       одни и те же контракты для Android и iOS
  build.gradle.kts            Android, iPhone ARM64, Apple Silicon и Intel симуляторы
iosApp/
  PetCare.xcodeproj/           готовый Xcode-проект со схемой PetCare
  Sources/                    SwiftUI-оболочка и Info.plist
  UITests/                    проверка входа, двух пространств и выхода на iOS
scripts/check-ios.sh           сборка iOS и тесты на доступном iPhone-симуляторе
```

Стек: Kotlin 2.2.21, Compose Multiplatform 1.8.2, Lifecycle 2.9.0, Ktor 3.1.3, kotlinx-datetime 0.6.2.
Java/Android API используются только в `androidMain` и Android-приложении.
Общий код не зависит от `java.time`, Android Context или Android Activity.
Android: **8.0 / API 26+**, compile/target SDK 35. iOS: **15.0+**, iPhone и iPad.

## Запуск Android

1. Клонировать репозиторий и выбрать ветку:
   ```sh
   git clone https://github.com/K1ll4a/petcare-android.git
   cd petcare-android
   git switch feature/android-ios
   ```
2. Открыть **корневую папку** с `settings.gradle.kts` в Android Studio Narwhal 2025.1.1 или новее. Gradle JDK — **17**.
3. Установить Android SDK Platform 35, Build-Tools 35.0.0 и Platform-Tools.
4. Запустить конфигурацию **app** на телефоне или эмуляторе Android API 26+.

Для терминала указать `ANDROID_HOME` или создать исключённый из Git `local.properties` с `sdk.dir=/absolute/path/to/sdk`:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:testDebugUnitTest
./gradlew :app:lintDebug :shared:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.petcare.app/.MainActivity
```

При подключённом эмуляторе/устройстве: `./gradlew :app:connectedDebugAndroidTest`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Запуск iOS

Нужен Mac с **полным Xcode** и установленным iOS Simulator. Одних Command Line Tools недостаточно.
Рекомендуется Xcode 16.4 или 26.0, JDK 17; Gradle Wrapper включён в репозиторий.

1. Открыть Xcode, завершить установку компонентов и установить iOS Simulator через Settings → Components.
2. Открыть **`iosApp/PetCare.xcodeproj`**, выбрать схему **PetCare** и доступный iPhone-симулятор.
3. Нажать **Run**. Build Phase автоматически вызовет `:shared:embedAndSignAppleFrameworkForXcode`.
   Общий Kotlin-модуль собирается в `PetCareShared.framework`; SwiftUI отображает его `MainViewController`.
4. Apple Developer Team для симулятора не требуется. Для физического iPhone выбрать свой Team в Signing & Capabilities
   и при необходимости уникальный Bundle Identifier.

Для проверки из терминала:

```sh
bash scripts/check-ios.sh
```

Скрипт выбирает доступный iPhone-симулятор, запускает общие Native-тесты для архитектуры компьютера,
затем собирает iOS-приложение и выполняет XCTest сценарий входа, переключения пространств и выхода.
Результаты: `iosApp/build/TestResults.xcresult`, приложение симулятора — в `iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/PetCare.app`.
Этот `.app` предназначен для симулятора; установка на физическое устройство требует подписи Apple.

## Учебная демонстрация на обеих платформах

Сервер для демонстрации не нужен. Для демо-аккаунтов пароль **`demo12345`**:

| Email | Пространства |
|---|---|
| `grisha@example.com` | «Семья»: владелец, «Дача»: участник |
| `anna@example.com` | «Семья»: участник |
| `oleg@example.com` | «Дача»: владелец |

Нажать **«Заполнить демо-аккаунт»**, затем **«Войти»**.
Проверить «Семью» → профиль Tom → форму задачи → «Пространства» → «Дачу»: питомцы и задачи меняются.
Новый аккаунт создаётся без пространств. Можно создать своё пространство или присоединиться по коду другого аккаунта.
Владелец управляет отдельными одноразовыми кодами на 24 часа; участнику управление приглашениями недоступно.
Выход с подтверждением очищает защищённый раздел и отменяет его запросы. Повторный вход открывает список пространств.

**Ограничения учебного режима:** аккаунты, членство и приглашения сохраняются локально на конкретном устройстве.
Синхронизация Android ↔ iOS появится после подключения Backend. Для проверки приглашения переключайтесь между
аккаунтами на одном устройстве. Питомцы и задачи сохраняются в памяти процесса и после завершения процесса
восстанавливаются из общей JSON-фикстуры `shared/.../data/fake/DemoFixture.kt`.
Активная сессия и пароль формы не сохраняются. Формы при закрытии/пересоздании экрана не восстанавливаются.
На iOS возврат выполняется видимыми кнопками «Назад», «К задачам», «Пространства».

Настройки ошибок: `DevConfig` в `shared/src/commonMain/kotlin/com/petcare/app/AppContainer.kt`.
`FAKE_SCENARIO`: NORMAL / EMPTY / ERROR / SLOW; `FAKE_SAVE_MODE`: OK / SERVER_ERROR / NO_RESPONSE.
Для полного сброса Android очистить хранилище приложения; на iOS удалить приложение и установить заново.

## Подключение Backend и работа команды

`AccountRepository`, `SpaceRepository`, `PetRepository` и `TaskRepository` общие.
Согласованная серверная реализация подключается в общем `AppContainer` сразу для двух платформ.
`KtorHttpTransport` использует OkHttp на Android и Darwin/NSURLSession на iOS; bearer-токен передаётся через `TokenProvider`.
Токен настоящего аккаунта потребуется хранить в платформенном защищённом хранилище; учебный режим токен не создаёт.
Автоматический вход тестовым пользователем отсутствует. Форматы `PetCareApi`, DTO и допущения `TaskEndpoints` сохранены.

Экраны и их логику изменять в **`shared/src/commonMain`**, а не создавать вторую копию для iOS.
Мобильные разработчики работают в этом репозитории; при смене интерфейса проверяют обе платформы.
Настоящая авторизация, межустройственная синхронизация, удаление участника и передача владения ещё не реализованы.

Проверки и ограничения миграции: [docs/multiplatform.md](docs/multiplatform.md).
Историческая проверка Android до миграции: [docs/verification.md](docs/verification.md).
GitHub Actions: **Android** собирает APK и тесты; **iOS** собирает приложение симулятора и выполняет Native/XCTest проверки.
