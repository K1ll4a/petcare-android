# Переход PetCare на Android и iOS

Дата начала: 5 ноября 2026 года. Ветка: `feature/android-ios`.
Основа — общий `main` после объединения работы двух мобильных разработчиков.

## Изменения

- `shared/commonMain`: все экраны Compose, навигация, ViewModel, контроллеры, модели, репозитории и формат JSON.
- Android Context и SharedPreferences вынесены в `androidMain`; iOS хранит учебные метаданные в NSUserDefaults.
- `java.time` заменён на kotlinx-datetime. Строгий разбор даты/времени и русские подписи едины для двух платформ.
- PBKDF2-HMAC-SHA256 и генерация случайных кодов используют Java crypto на Android и CommonCrypto/Security на iOS.
  Алгоритм и параметры сохранены: старые локальные Android-аккаунты остаются совместимыми.
- Переносимый HTTP-клиент Ktor с платформенными движками OkHttp и Darwin заменил Android-only транспорт.
- SwiftUI-оболочка в готовом Xcode-проекте открывает общий Compose UIViewController.
- Цели Kotlin/Native: физический iPhone ARM64, симулятор Apple Silicon ARM64 и симулятор Intel x86_64.
- Добавлены CI для iOS и общие тесты, которые исполняются и на Android/JVM, и на iOS Simulator.

## Проверки

Локальная сборка и проверки прошли на JDK 17, Gradle 8.13, AGP 8.11.1:

| Проверка | Результат |
|---|---|
| Android APK и APK UI-тестов | Собраны |
| Прежние JVM-тесты приложения | 99 прошли |
| Общие Kotlin Multiplatform контракты на JVM | 9 прошли |
| Android UI на Pixel 7 AVD, Android 15 / API 35 | 2 прошли |
| Lint приложения и общего Android-модуля | Без ошибок |
| Common Kotlin metadata | Компилируется |

9 общих контрактов включают PBKDF2 с ASCII и Unicode паролями, системную генерацию кодов,
часовые пояса и DST, строгую валидацию даты/времени, роли и приглашения, заголовки HTTP и обработку 401.
Один исходный набор общих контрактов также запускается на iOS Simulator в CI.

Локально полный Xcode и iOS Simulator отсутствуют: установлен только Command Line Tools.
`project.pbxproj` и `Info.plist` проверены `plutil`. Нативная компиляция и запуск iOS проверяются отдельным GitHub Actions workflow.
Первый iOS-прогон обнаружил неверное имя импорта системной CommonCrypto в Kotlin/Native.
Импорт исправлен по определению `CommonCrypto.def` Kotlin 2.2.21: пакет `platform.CoreCrypto`.
[Повторная iOS-проверка](https://github.com/K1ll4a/petcare-android/actions/runs/41957934865) запускает
сначала общие Native-контракты, затем отдельно сборку приложения и XCTest на iPhone-симуляторе.
Общие Native-контракты прошли, приложение и XCTest runner собраны. UI-прогон завершился
после запуска приложения: в Info.plist отсутствовал обязательный для Compose параметр
`CADisableMinimumFrameDurationOnPhone`. Он добавлен вместе с SwiftUI Scene Manifest;
Debug собирает только активную архитектуру симулятора. Итоговый UI-прогон повторяется.

## Что продолжает требовать Backend

Учебные аккаунты и пространства пока локальные. На Android и iOS работают одни сценарии,
но данные двух устройств не синхронизируются. Реальная авторизация и серверные приглашения подключаются
в общей точке зависимостей после появления фактического контракта.

## Документация

- [Инструкция Android и iOS](../README.md)
- [Совместимость Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Интеграция Kotlin framework с Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)
