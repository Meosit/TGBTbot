package com.tgbt.settings

import com.tgbt.BotJson
import com.tgbt.grammar.*
import java.util.concurrent.atomic.AtomicReference

enum class Setting(private val default: String) {
    VK_COMMUNITY_ID("-57536014"),
    FORWARDING_ENABLED("false"),
    TARGET_CHANNEL("@tegebetetest"),
    CONDITION_EXPR(BotJson.encodeToString(Expr.serializer(), Or(Likes(ConditionalOperator.GREATER_OR_EQUAL, 1000), Reposts(ConditionalOperator.GREATER_OR_EQUAL, 15)))),
    CHECK_PERIOD_MINUTES("10"),
    RETENTION_PERIOD_DAYS("15"),
    POST_COUNT_TO_LOAD("300"),
    USE_PHOTO_MODE("true"),
    FOOTER_MD(""),
    SEND_STATUS("true"),
    // VK freeze notifications
    NOTIFY_FREEZE_TIMEOUT("true"),
    NOTIFY_FREEZE_SCHEDULE("true"),
    VK_FREEZE_TIMEOUT_MINUTES("90"),
    VK_FREEZE_IGNORE_START(""),
    VK_FREEZE_IGNORE_END(""),
    SEND_FREEZE_STATUS("true"),
    VK_FREEZE_MENTIONS("anon"),
    VK_SCHEDULE("5:00 Улиточка"),
    VK_SCHEDULE_ERROR_MINUTES("5"),
    // Suggestions
    SUGGESTIONS_ENABLED("true"),
    EDITOR_CHAT_ID("-1001519413163"),
    USER_EDIT_TIME_MINUTES("10"),
    USER_SUGGESTION_DELAY_MINUTES("30"),
    SUGGESTION_POLLING_DELAY_MINUTES("10"),
    SEND_SUGGESTION_STATUS("true"),
    GATEKEEPER("anon");

    fun save(value: String) = set(this, value)

    fun str(): String = settings.get().getOrDefault(name, default)

    fun bool(): Boolean = settings.get().getOrDefault(name, "false").toBoolean()
    fun int(): Int = str().toInt()
    fun long(): Long = str().toLong()


    companion object {

        private val settings by lazy {
            AtomicReference(SettingStore.selectAll())
        }

        private operator fun set(key: Setting, value: String) {
            if (SettingStore.insertOrUpdate(key.name, value)) {
                settings.updateAndGet { it + (key.name to value) }
            }
        }
    }
}