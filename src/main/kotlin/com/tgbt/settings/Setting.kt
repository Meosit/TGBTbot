package com.tgbt.settings

enum class Setting {
    VK_COMMUNITY_ID,
    FORWARDING_ENABLED,
    TARGET_CHANNEL,
    CONDITION_EXPR,
    CHECK_PERIOD_MINUTES,
    RETENTION_PERIOD_DAYS,
    POST_COUNT_TO_LOAD,
    USE_PHOTO_MODE,
    FOOTER_MD,
    SEND_STATUS,
    // VK freeze notifications
    VK_FREEZE_TIMEOUT_MINUTES,
    VK_FREEZE_MENTIONS,
    VK_SCHEDULE,
    VK_SCHEDULE_ERROR_MINUTES,
    // suggestions
    SUGGESTIONS_ENABLED,
    EDITOR_CHAT_ID,
    USER_EDIT_TIME_MINUTES,
    USER_SUGGESTION_DELAY_MINUTES,
    SUGGESTION_POLLING_DELAY_MINUTES,
    SEND_PROMOTION_FEEDBACK,
    SEND_DELETION_FEEDBACK,
    SEND_SUGGESTION_STATUS
}