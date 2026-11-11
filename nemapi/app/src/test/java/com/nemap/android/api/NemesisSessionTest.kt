package com.nemap.android.api

import org.junit.Assert.*
import org.junit.Test

class NemesisSessionTest {
    @Test fun acceptsOnlyDeepSeekChatUrls() {
        assertTrue(NemesisSession.validConversationUrl("https://chat.deepseek.com/a/chat/s/12345678-abcd"))
        assertFalse(NemesisSession.validConversationUrl("https://chat.deepseek.com.evil.test/a/chat/s/12345678"))
        assertFalse(NemesisSession.validConversationUrl("http://chat.deepseek.com/a/chat/s/12345678"))
        assertFalse(NemesisSession.validConversationUrl("https://chat.deepseek.com/a/chat/s/12345678?x=1"))
        assertFalse(NemesisSession.validConversationUrl("https://chat.deepseek.com/a/chat/s/../admin"))
    }
}
