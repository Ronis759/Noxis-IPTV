# Анализ багов в проекте Noxis-IPTV

## 🔴 КРИТИЧЕСКИЕ ОШИБКИ

### 1. **Утечка памяти в SleepTimer (MainActivity.kt:2587)**
**Проблема:** При вызове `sleepTimer.cancel()` в `onDestroy()` не гарантируется отмена всех операций.
- `handler.removeCallbacks()` может быть вызван до завершения рunnables
- `onTick` callback хранит ссылку на `MainActivity`, предотвращая garbage collection

**Последствие:** Память не освобождается при уничтожении Activity.

**Решение:**
```kotlin
override fun onDestroy() {
    sleepTimer.cancel()
    super.onDestroy()
    ui.removeCallbacksAndMessages(null)  // Очистить ВСЕ callbacks Handler'а
    Iptv.onUa = null
    player.release()
}
```

---

### 2. **Race condition в UpdateFlow.kt (проверка обновлений)**
**Проблема:** Поле `available` помечено `@Volatile`, но это недостаточно для безопасных изменений:
```kotlin
@Volatile var available: Updater.Info? = null
// ...
if (newer) { save(info!!); available = info }  // Race condition!
```

**Последствие:** В многопоточной среде может произойти потеря обновления или несогласованное состояние.

**Решение:** Использовать synchronization:
```kotlin
private val updateLock = Any()
synchronized(updateLock) {
    if (newer) { save(info!!); available = info }
}
```

---

### 3. **Неправильная работа SleepTimer при прерывании таймера**
**Проблема:** В `SleepTimer.start()` callback `onTick` может быть вызван после `cancel()`:

```kotlin
// В cancel():
onTick = null  // Устанавливаем в null

// Но tickRunnable может уже быть в очереди Handler'а:
handler.postDelayed(this, 1000)  // Может быть вызван ПОСЛЕ cancel()
if (isActive) {
    onTick?.invoke(remaining)  // NPE если onTick = null
}
```

**Последствие:** NPE или игнорирование отмены таймера.

**Решение:** Проверять состояние перед вызовом:
```kotlin
tickRunnable = object : Runnable {
    override fun run() {
        if (isActive && onTick != null) {
            onTick?.invoke(remaining)
            handler.postDelayed(this, 1000)
        }
    }
}
```

---

## ⚠️ СЕРЬЕЗНЫЕ ПРОБЛЕМЫ

### 4. **Некорректная обработка состояния restoring**
**Проблема:** Флаг `restoring` не сбрасывается после завершения восстановления:

```kotlin
private var restoring = false

private fun applyRestore(m: Map<String, Any>) {
    restoring = true
    val ed = prefs.edit()
    // ... изменение настроек ...
    ed.commit()
    Session.clear()
    recreate()  // Activity пересоздается, но restoring остается true?
}

override fun onStop() {
    if (restoring) return  // Спасает только ОДИН раз
}
```

**Последствие:** Если восстановление произойдет во второй раз, флаг может остаться true.

---

### 5. **Потенциальная утечка памяти в EpgCache.save()**
**Проблема:** Временный файл может остаться на диске при исключении:

```kotlin
fun save(cf: File, key: String, src: Long, ...) {
    val tmp = File(cf.parentFile, cf.name + ".tmp")
    try {
        // Запись во временный файл
    } finally {
        tmp.delete()  // Может не удалиться при исключении в IO
    }
}
```

**Решение:** Использовать `tmp.deleteOnExit()` как резервный вариант.

---

### 6. **Неправильная работа .apply() вместо .commit() в SharedPreferences**
**Проблема:** Используется много `.apply()` без `.commit()`:

```kotlin
prefs.edit().putString("pl", pl).putString("epg", ed).apply()  // Асинхронно!
// Сразу после этого может быть recreate(), но изменения еще не записаны
```

**Последствие:** Данные могут не сохраниться при краше приложения.

**Рекомендация:** Критичные настройки должны использовать `.commit()`:
```kotlin
prefs.edit()
    .putString("pl", pl)
    .putString("epg", ed)
    .commit()  // Синхронно гарантирует сохранение
```

---

## 🟡 УМЕРЕННЫЕ ПРОБЛЕМЫ

### 7. **Thread leak в загрузке плейлистов и программы**
**Проблема:** Потоки создаются через `Thread { ... }.start()` без контроля:

```kotlin
Thread {
    try {
        val loaded = repo.loadPlaylist(pl)
        // ...
    } catch (e: Exception) {
        runOnUiThread { /* ... */ }
    }
}.start()
```

**Последствие:** При быстром переключении плейлистов может быть много активных потоков.

**Решение:** Использовать `loadGen` для отмены (уже есть, но неполная):
```kotlin
if (gen != loadGen) return@Thread  // Проверка должна быть ДО любых операций
```

---

### 8. **Возможный NPE в showSleepTimerDialog()**
**Проблема:** При быстром вызове диалога несколько раз:

```kotlin
private fun showSleepTimerDialog() {
    val overlay = FrameLayout(this).apply {
        setBackgroundColor(0xDD000000.toInt())
        addView(dialog, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }
    root.addView(overlay, ...)  // Если root не инициализирован?
}
```

**Решение:** Проверка инициализации:
```kotlin
if (!::root.isInitialized) return
```

---

### 9. **Проблема с UI-обновлениями в фоновых потоках**
**Проблема:** `runOnUiThread` вызывается не всегда безопасно:

```kotlin
runOnUiThread {
    if (gen == loadGen || isFinishing || isDestroyed) return@runOnUiThread
    // Но между проверкой и выполнением Activity может быть уничтожена
    applyChannels(list.ch, ...)  // NPE если view была удалена
}
```

**Решение:** Дополнительная проверка в начале блока.

---

## 📋 СПИСОК ИСПРАВЛЕНИЙ

| № | Файл | Проблема | Приоритет |
|---|------|---------|----------|
| 1 | SleepTimer.kt | Утечка памяти в callback | 🔴 HIGH |
| 2 | UpdateFlow.kt | Race condition при сохранении обновления | 🔴 HIGH |
| 3 | SleepTimer.kt | NPE при вызове onTick после cancel() | 🔴 HIGH |
| 4 | MainActivity.kt | Флаг restoring не сбрасывается | ⚠️ MEDIUM |
| 5 | EpgCache.kt | Временный файл может остаться на диске | ⚠️ MEDIUM |
| 6 | MainActivity.kt | Использование .apply() вместо .commit() | ⚠️ MEDIUM |
| 7 | MainActivity.kt | Thread leak при загрузке | 🟡 LOW |
| 8 | MainActivity.kt | Возможный NPE в showSleepTimerDialog() | 🟡 LOW |
| 9 | MainActivity.kt | Небезопасные UI-обновления из фона | 🟡 LOW |

