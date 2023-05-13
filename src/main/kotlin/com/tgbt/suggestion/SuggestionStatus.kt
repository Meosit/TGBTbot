package com.tgbt.suggestion

enum class SuggestionStatus {
    // created and waiting for a while for possible modifications
    PENDING_USER_EDIT,
    // scheduled job is able to pick up the finalized post
    READY_FOR_SUGGESTION,
    // waiting for editors to review and either repost or delete the post, anyway after that the post is deleted form DB
    PENDING_EDITOR_REVIEW,
    // action taken, but schedule to post anonymously after chosen amount of time
    SCHEDULE_ANONYMOUSLY,
    // action taken, but schedule to post publicly after chosen amount of time
    SCHEDULE_PUBLICLY,
}
