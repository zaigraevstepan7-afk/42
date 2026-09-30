# Antigravity

Клиент с каркасом экрана ChatGPT Android: холст `#212121`, «Продолжить с Google», «Спросите что угодно», «Новый чат».

Вход один. Кнопка открывает OAuth десктопного клиента Antigravity (`127.0.0.1:51121`) и берёт квоту Cloud Code Assist, не ключ Gemini API и не сессию OpenAI. Без root экран входа не открывается.

Цвета тёмной темы взяты из пакета: холст `#212121`, пузырь и поле `#303030`, текст `#FFFFFF`, плейсхолдер `#8F8F8F`, меню `#0D0D0D`. Знак, кнопка Google, карандаш, микрофон и полоски голоса — векторы из того же APK.

Сборка: скопировать `secrets.properties.example` в `secrets.properties` и вписать client id и secret десктопного клиента Antigravity (их публикует CLIProxyAPI в `internal/auth/antigravity/constants.go`). Дальше Android Studio, Run. `applicationId` — `com.antigravity.android`, ярлык «ChatGPT», приложение из Play не заменяется.
