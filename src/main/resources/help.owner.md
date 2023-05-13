You're the owner of this bot, so you're able to change the following settings:

/settings - view current settings

For post forwarding:
/forwarding `true|false` - global switch, set to false if you want to disable VK post forwarding
/vk\_id `<id number>` - set VK community ID to fetch posts from. Usually it's negative number, google for more info
/check `<minutes>` - set period in minutes to check for new VK posts or posts that reached the condition, default to 10 minutes
/retention `<days>` - set period in days to delete posts from database created earlier than specified number of days, default to 15
/channel `<@channelusername>` - set channel where to send VK posts, bot must be added as admin to this channel, username must start with `@`
/count `<posts to load>` - number of posts to load from VK on every check, default to 300
/condition `condition expression` - modify expression to filter posts which should be forwarded to telegram, there are 4 available criteria: `likes`, `commnets`, `views`, `reposts`. Supported operators: `and`, `or`, `not`. Example expression: `(likes >= 1000) or (reposts >= 15)`
/footer `footer markdown` - modify/add footer markdown to be added at the end of each message (with one empty line delimiter) 
/send\_stats `true|false` - if true, will send a count (if more than 1) of forwarded posts for every owner each time it happens 
/force\_forward - trigger a post forwarding manually as a result of this command

For user suggestions forwarding:
/suggestions `<true|false>` - global switch for suggestions
/gatekeeper `<tg mention>` - username or the person which will be visible for users to solve ban-related requests
/editors\_chat `<id_number>`- set the editors chat where the suggestions will be posted for approval 
/force\_pool\_suggestions - trigger a suggestion pooling from DB to editors chat
/suggestion\_pooling `<minutes>` - time between new suggestions check
/suggestion\_delay `<minutes>` - cool down between user suggestions
/suggestion\_edit `<minutes>` - time for user to edit his/her suggestion
/clean\_old\_suggestions `<days>` - delete suggestion from DB which older than provided amount of days

For VK freeze timeout notifications (when no new posts appear):
/notify\_freeze\_timeout `<true|false>` - switch for sending freeze notifications by timeout
/notify\_freeze\_schedule `<true|false>` - switch for sending freeze notifications by schedule miss  
/send\_freeze\_status `<true|false>` - send additional notification in owner bot chat for every schedule or timeout miss  
/vk\_freeze\_timeout `<minutes>` - if no VK posts after this timeout appear in VK, send notification. Note that there is a possible delay of VK check period.
/vk\_freeze\_ignore `HH:MM-HH:MM` - period when all freeze notifications are prevented
/vk\_freeze\_mentions `<tg mentions>`- basically a free text to be added to each notification, to mention responsible people. 
/vk\_schedule `<vk schedule>`- a list of schedule items in format `00:00 <@nickname|text>`, each on a new line
/vk\_schedule\_error `<minutes>` - period around a schedule slot when no notification sent
/last\_day\_schedule - print schedule along with missed slots for the last day
/last\_day\_missed - print all missed slots for the last day
