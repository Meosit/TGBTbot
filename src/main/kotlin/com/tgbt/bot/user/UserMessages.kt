package com.tgbt.bot.user

import com.tgbt.misc.loadResourceAsString

object UserMessages {

    val helpMessage = loadResourceAsString("user/help.md")
    val startMessage = loadResourceAsString("user/start.md")

    val postPromotedMessage = loadResourceAsString("user/post.promoted.md")
    val postDiscardedMessage = loadResourceAsString("user/post.discarded.md")
    val postDiscardedWithCommentMessage = loadResourceAsString("user/post.discarded.comment.md")
    val postUpdatedMessage = loadResourceAsString("user/post.updated.md")
    val postDeletedMessage = loadResourceAsString("user/post.deleted.md")
    val postPhotoDeletedMessage = loadResourceAsString("user/post.photo.deleted.md")
    val photoUpdatedAttachmentMessage = loadResourceAsString("user/post.photo.attached.md")
    val photoUpdatedMessage = loadResourceAsString("user/post.photo.updated.md")

    val suggestionsDisabledErrorMessage = loadResourceAsString("user/error.disabled.md")
    val updateTimeoutErrorMessage = loadResourceAsString("user/error.timeout.md")
    val invalidPhotoErrorMessage = loadResourceAsString("user/error.invalid.photo.md")
    val internalErrorMessage = loadResourceAsString("user/error.internal.md")
    val emptyErrorMessage = loadResourceAsString("user/error.invalid.empty.md")
    val sizeErrorMessage = loadResourceAsString("user/error.invalid.size.md")
}