# MyVPN

Android-приложение: одна кнопка «Подключиться», весь трафик устройства идёт через вшитый HTTP-прокси.

## Как устроено
- `MyVpnService` — Android `VpnService`, поднимает TUN.
- `hev-socks5-tunnel` (нативная библиотека, собирается в CI) — превращает пакеты TUN в SOCKS5-соединения; DNS разрешается на стороне прокси (mapdns).
- `ProxyBridge` — локальный SOCKS5 на 127.0.0.1:10808, переводит каждое соединение в `HTTP CONNECT` к вашему прокси с Basic-авторизацией.
- Данные прокси попадают в приложение при сборке через `BuildConfig` (из GitHub Secrets) и в репозитории не хранятся.

## Ограничения
- HTTP-прокси не поддерживает UDP: звонки, игры и QUIC работать не будут (браузеры откатываются на TCP).
- Логин и пароль можно вытащить из готового APK. Выдавайте отдельные учётки на пользователей или ограничьте трафик на стороне прокси.
- Репозиторий держите **приватным**.

## Сборка на GitHub
1. Создайте **приватный** репозиторий и залейте в него содержимое этой папки (ветка `main`).
2. Settings → Secrets and variables → Actions → New repository secret, добавьте:
   - `PROXY_HOST`
   - `PROXY_PORT`
   - `PROXY_USER`
   - `PROXY_PASS`
3. (необязательно, чтобы APK обновлялся поверх старого) секреты `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`.
   Ключ создаётся так: `keytool -genkeypair -keystore release.jks -alias key -keyalg RSA -keysize 2048 -validity 10000`,
   затем `base64 -w0 release.jks` — это значение для `KEYSTORE_BASE64`. Пароль ключа должен совпадать с паролем хранилища.
4. Actions → Build APK → Run workflow. Готовый APK — в Artifacts (`MyVPN-apk`).

## Название и package name
Приложение называется `MyVPN`, пакет `com.myvpn.app`. Поменять: `app/src/main/res/values/strings.xml` и `namespace`/`applicationId` в `app/build.gradle.kts`
(папку `app/src/main/java/com/myvpn/app` и `package` в файлах придётся переименовать; `hev/htproxy` не трогать).
