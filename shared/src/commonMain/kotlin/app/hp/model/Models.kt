package app.hp.model

/**
 * Тип записи в хранилище
 */
enum class EntryType {
    PASSWORD,      // Логин/пароль
    TOTP,          // Коды подтверждения
    WIFI,          // Wi-Fi сеть
    NOTE,          // Заметка
    CARD           // Банковская карта (на будущее)
}

/**
 * Уровень доступа для участника к конкретной записи
 */
enum class AccessLevel {
    NONE,          // Нет доступа (запись не видна)
    AUTOFILL_ONLY, // Только автозаполнение, без просмотра
    VIEW           // Полный просмотр и копирование
}

/**
 * Роль участника в хранилище
 */
enum class ParticipantRole {
    OWNER,         // Владелец хранилища
    ADMIN,         // Администратор (управление участниками, группами)
    MEMBER         // Обычный участник
}

/**
 * Статус записи (для синка и tombstone)
 */
enum class EntryStatus {
    ACTIVE,
    DELETED        // Tombstone - запись удалена, но нужна для синка
}
