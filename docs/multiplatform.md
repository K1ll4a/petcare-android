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

Первая Android-сборка после переноса прошла: `:app:assembleDebug`, 99 прежних unit-тестов,
`:shared:compileCommonMainKotlinMetadata`. Итоговый прогон общих тестов, Lint и Android UI будет указан после завершения.

Локально полный Xcode и iOS Simulator отсутствуют: установлен только Command Line Tools.
`project.pbxproj` и `Info.plist` проверены `plutil`. Нативная компиляция и запуск iOS проверяются отдельным GitHub Actions workflow.
Положительный результат iOS пока не зафиксирован; наличие исходников не заменяет сборку и запуск.

## Что продолжает требовать Backend

Учебные аккаунты и пространства пока локальные. На Android и iOS работают одни сценарии,
но данные двух устройств не синхронизируются. Реальная авторизация и серверные приглашения подключаются
в общей точке зависимостей после появления фактического контракта.

## Документация

- [Инструкция Android и iOS](../README.md)
- [Совместимость Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Интеграция Kotlin framework с Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)
