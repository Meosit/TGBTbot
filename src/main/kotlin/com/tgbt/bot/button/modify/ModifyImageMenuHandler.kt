package com.tgbt.bot.button.modify

import com.tgbt.BotJson
import com.tgbt.bot.BotCommand
import com.tgbt.bot.MessageContext
import com.tgbt.bot.button.CallbackNotificationText
import com.tgbt.misc.isImageUrl
import com.tgbt.telegram.TelegramClient
import com.tgbt.telegram.api.InlineKeyboardButton
import com.tgbt.telegram.api.InlineKeyboardMarkup
import com.tgbt.telegram.api.Message
import com.tgbt.telegram.api.imageId

abstract class ModifyImageMenuHandler(
    category: String,
    private val searchByAuthor: Boolean,
): ModifyMenuHandler(category, "M_IMAGE"), BotCommand {

    override fun isValidPayload(payload: String): Boolean = payload in editComments

    override suspend fun handleButtonAction(
        message: Message,
        pressedBy: String,
        validPayload: String
    ) = modifyPost(message, searchByAuthor) { it.copy(imageId = imageUrls.getValue(validPayload)) }

    override suspend fun renderNewMenu(message: Message, pressedBy: String): CallbackNotificationText {
        val keyboard = sequence {
            val buttons = editComments.map { (key, comment) ->
                InlineKeyboardButton(comment, callbackData(key))
            }
            yield(listOf(buttons[0]))
            val onlyImages = buttons.drop(1)
            for (i in onlyImages.indices step 2) {
                yield((0 until 2).mapNotNull { onlyImages.getOrNull(i + it) })
            }
            yield(listOf(retrieveMainMenuHandler().backButton))
        }.toList().let { InlineKeyboardMarkup(it) }
        val keyboardJson = BotJson.encodeToString(InlineKeyboardMarkup.serializer(), keyboard)
        TelegramClient.editChatMessageKeyboard(message.chat.id.toString(), message.id, keyboardJson)
        return null
    }

    override val command: String = "/image"
    override fun canHandle(context: MessageContext): Boolean = with(context) {
        messageText.startsWith(command) || messageText.isImageUrl() || message.imageId != null
    }

    override suspend fun MessageContext.handle() {
        val url = messageText.removePrefix(command)
        if (replyMessage != null && (url.isImageUrl() || message.imageId != null)) {
            modifyPost(replyMessage, searchByAuthor) { it.copy(imageId = message.imageId ?: messageText.trim()) }
        }
    }

    companion object {

        private val editComments = mapOf(
            "nopic" to "❌\uD83D\uDDD1 Удалить Картинку \uD83D\uDDD1❌",
            "default" to "\uD83D\uDDBC Дефолтный йоба",
            "laugh" to "\uD83D\uDDBC Смеющийся йоба",
            "rage" to "\uD83D\uDDBC Горелый йоба",
            "cry" to "\uD83D\uDDBC Слезливый йоба",
            "creep" to "\uD83D\uDDBC Криповый йоба",
            "doom" to "\uD83D\uDDBC Думер йоба",
            "doom" to "\uD83D\uDDBC Батя йоба",
            "twist" to "\uD83D\uDDBC Скрюченый йоба",
            "respect" to "\uD83D\uDDBC В точку йоба",
        )
        private val imageUrls = mapOf(
            "nopic" to null,
            "default" to "https://sun6.userapi.com/sun6-23/s/v1/ig2/Rw4gx5hgWBRBNpIfU6cSYDt07noe1MnICMR5BBLDfe9OKujnGAy7QAHtv3QnvvBUuo0DBjc9YZwbXrUWjJoNlFxU.jpg?size=551x551&quality=96&type=album",
            "laugh" to "https://sun6-21.userapi.com/impg/gqeV393Al3VYAL1PB0D1KWM41o48Jo3uBdniOQ/Ta4QytpxaAA.jpg?size=736x736&quality=95&sign=0d53e689938a9c1b0786a179e0127660&type=album",
            "rage" to "https://sun9-north.userapi.com/sun9-82/s/v1/ig2/MaNAvmjCaJR-WmE9-hTg-2JtW7UylT0TywvTYJl2CZMKfBvbqSbAsNI3-JWdFbAZhz-RYfkRNtG59OCgxOYFldEH.jpg?size=500x500&quality=95&type=album",
            "cry" to "https://sun6.userapi.com/sun6-20/s/v1/ig2/geSQmOIvkC_wJWs22X-mk63pbUV3h7Jhbk63EifU1dvf6w6PkN3mRZr8X2VjM3TtGkquon8FDDzYNTWp6YiEEKFh.jpg?size=521x500&quality=95&type=album",
            "creep" to "https://sun9-north.userapi.com/sun9-86/s/v1/if1/CECJKh38jpVAPrDqtZLg_OscWYZMmevrSk-duCX9fOLLpwqjNLaJPMM2XIVOcSEas0TlKvZf.jpg?size=736x736&quality=96&type=album",
            "doom" to "https://sun9-north.userapi.com/sun9-77/s/v1/ig2/hw2uLGMctR2RdMtqAYMmhD8aRWABELT4QpahlFapY1EtN7PmjlP5N2KMdqA4m3nACxFJMTRZfRit_JFa_RNIYnus.jpg?size=500x500&quality=96&type=album",
            "twist" to "https://sun6.userapi.com/sun6-23/s/v1/ig2/enK9f6D-j_7fXjBEYNaoujBGmWxP-R6ZqKZrq0UXFra1GH1gH6rrCttREQn3n7NSbCT8hglYe6LKhrrPQTUn1pxr.jpg?size=604x604&quality=95&type=album",
            "respect" to "https://sun6-23.userapi.com/impf/wN2Lq8VemYL09fyqoFYg_MvqbZMXhC50rB06Fw/8o2HhJizVoQ.jpg?size=604x463&quality=96&sign=42ce40dbdd2e914f0ef0599387e93217&type=album",
        )

    }
}