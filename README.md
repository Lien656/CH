# CH

Чат с Claude (Android). Тот же функционал, что и Kaelhome: память [ЗАПОМНИ], фото/видео/файлы, дата и время, уведомления. Модель: **Anthropic Claude** (claude-3-5-sonnet-20241022).

## Требования

- **JDK 17** (для сборки)
- Android: minSdk 24, targetSdk 34

## Клонирование и сборка

```bash
git clone https://github.com/Lien656/CH.git
cd CH
```

**Сборка:** `gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).  
APK: `app/build/outputs/apk/debug/app-debug.apk`.

В Android Studio: укажи **JDK 17** в Project Structure → SDK Location.

## GitHub Actions

При пуше в `main`/`master` собирается APK. Скачать: **Actions** → run → **Artifacts**.

## Что внутри

- API: **Anthropic** (x-api-key). Базовый URL по умолчанию: https://api.anthropic.com (в настройках можно сменить для прокси).
- Личность: Claude из system prompt (короткие ответы, Лиен, память, код, фото, файлы).
- Память: блок [ЗАПОМНИ: …] в ответе сохраняется в ch_memory.txt и подставляется в системный промпт.
- Цвета по ТЗ: фон #121111, пузыри ИИ/пользователя полупрозрачные, имя Claude #244548, Ты #1C2344.
- Вложения, консоль, дата/время в промпте, уведомления при ответе в фоне.
