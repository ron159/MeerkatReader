package com.capyreader.app.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewInterfaceTest {
    @Test
    fun `text copy dialog receives trimmed bounded content`() {
        val received = mutableListOf<String>()
        val webViewInterface = WebViewInterface(
            navigateToMedia = {},
            onRequestLinkDialog = {},
        ).also {
            it.onRequestTextCopyDialog = received::add
        }

        webViewInterface.showTextCopyDialog("  translated paragraph  ")
        webViewInterface.showTextCopyDialog("   ")
        webViewInterface.showTextCopyDialog("x".repeat(20_001))

        assertEquals(listOf("translated paragraph"), received)
    }
}
