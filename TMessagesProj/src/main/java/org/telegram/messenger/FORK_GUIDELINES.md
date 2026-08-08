# Fork Guidelines

Правила и советы по поддержке форка Telegram Android.

---

## Принцип №1: ForkConfig

Все кастомные модификации **оборачиваются проверками** флагов из [`ForkConfig.java`](file:///Users/ilia.maksimov/dev/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/ForkConfig.java).

**Никогда** не удаляй и не заменяй оригинальный код напрямую. Вместо этого:

```java
// ✅ Правильно — оригинальный код сохранён, обёрнут в if
if (!ForkConfig.MY_FEATURE) {
    // оригинальный код Telegram
}

// ❌ Неправильно — оригинальный код удалён
// (здесь раньше был код Telegram, а теперь пустота)
```

Для добавления нового поведения:

```java
if (ForkConfig.MY_FEATURE) {
    // твой новый код
} else {
    // оригинальный код Telegram
}
```

### Преимущества

- `grep -r ForkConfig` — мгновенно найдёт все кастомизации
- Поменял `true` → `false` — и фича отключена, код работает как оригинал
- Merge-конфликты минимальны: upstream меняет код *внутри* if-блока, а не саму обёртку

---

## Принцип №2: Отдельные файлы

Если фича требует много нового кода — **выноси в отдельный класс/файл**.

```
org.telegram.messenger.ForkConfig        — конфиг флагов
org.telegram.messenger.fork.*            — новые хелперы, утилиты, UI
```

Файлы, которых нет в upstream, **никогда не конфликтуют**.

---

## Принцип №3: Атомарные коммиты

Каждая фича = **один коммит** с понятным описанием.

```bash
git log --oneline
# abc1234 feat: always show attach button at bottom
# def5678 feat: custom notification sound
# ghi9012 fix: attach button margin when scheduled visible
```

Это даёт:
- Простой `git rebase` — каждый коммит применяется отдельно
- Легко откатить одну фичу: `git revert abc1234`
- Понятную историю изменений

---

## Принцип №4: Git-workflow обновления upstream

### Настройка (один раз)

```bash
git remote add upstream https://github.com/nicegram/nicegram-android.git
# или DrKLO/Telegram, в зависимости от базы
```

### Обновление

```bash
git fetch upstream
git rebase upstream/master
```

### При конфликте

1. Открой файл с конфликтом
2. Найди `ForkConfig` — это маркер твоего изменения
3. Восстанови обёртку `if (!ForkConfig.X)` вокруг **нового** upstream-кода
4. `git add . && git rebase --continue`

### Если конфликтов много

```bash
# Откатить rebase и начать заново
git rebase --abort

# Или мерж вместо rebase (проще, но грязнее история)
git merge upstream/master
```

---

## Принцип №5: Минимальная поверхность изменений

Чем меньше строк ты меняешь в оригинальных файлах — тем меньше конфликтов.

| Подход | Строк изменено | Вероятность конфликта |
|--------|---------------|----------------------|
| Удалил блок кода | ~15 | Высокая |
| Обернул в `if (ForkConfig.X)` | ~2 | Низкая |
| Вынес в отдельный файл | 1 (вызов) | Минимальная |

---

## Чеклист перед коммитом

- [ ] Оригинальный код **не удалён**, а обёрнут в `ForkConfig`
- [ ] Новый флаг добавлен в `ForkConfig.java` с комментарием
- [ ] `grep -r ForkConfig` показывает только ожидаемые файлы
- [ ] Коммит содержит **одну** логическую фичу
- [ ] Описание коммита начинается с `feat:`, `fix:`, или `refactor:`
