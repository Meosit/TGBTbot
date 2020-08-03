You're the owner of this bot, so you're able to change the following settings:

/forwarding `true|false` - global switch, set to false if you want to disable VK post forwarding
/photomode `true|false` - switch to use photo mode, set to false if you want to send posts with photos and length <=1024 chars using invisible link (displayed at the end of the message)
/vkid `<id_number>` - set VK community ID to fetch posts from. Usually it's negative number, google for more info
/check `<minutes>` - set period in minutes to check for new VK posts or posts that reached the condition, default to 10 minutes
/retention `<days>` - set period in days to delete posts from database created earlier than specified number of days, default to 15
/channel `<@channelusername>` - set channel where to send VK posts, bot must be added as admin to this channel, username must start with `@`
/count `<posts_to_load>` - number of posts to load from VK on every check, default to 300
/condition `condition expression` - modify expression to filter posts which should be forwarded to telegram, there are 4 available criteria: `likes`, `commnets`, `views`, `reposts`. Supported operators: `and`, `or`, `not`. Example expression: `(likes >= 1000) or (reposts >= 15)`
/footer `footer markdown` - modify/add footer markdown to be added at the end of each message (with one empty line delimiter) 