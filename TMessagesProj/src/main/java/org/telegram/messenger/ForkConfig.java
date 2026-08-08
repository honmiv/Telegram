package org.telegram.messenger;

/**
 * Конфигурация форка. Все кастомные модификации оборачиваются
 * проверками флагов из этого класса, чтобы минимизировать
 * конфликты при обновлении upstream.
 */
public class ForkConfig {
    /** Скрепка (attachButton) всегда отображается внизу справа в поле ввода */
    public static final boolean ALWAYS_SHOW_ATTACH_BOTTOM = true;

    /** Отключение защиты контента (запрета пересылки, сохранения и скриншотов) */
    public static final boolean DISABLE_CONTENT_PROTECTION = true;
}
