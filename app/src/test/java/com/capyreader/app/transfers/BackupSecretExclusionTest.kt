package com.capyreader.app.transfers

import com.capyreader.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupSecretExclusionTest {
    @Test
    fun `manual backups exclude app credentials`() {
        assertTrue("ai_api_key" in SENSITIVE_APP_PREFERENCE_KEYS)
        assertTrue("wallabag_access_token" in SENSITIVE_APP_PREFERENCE_KEYS)
        assertTrue("webdav_backup_password" in SENSITIVE_APP_PREFERENCE_KEYS)
        assertTrue("password" in SENSITIVE_ACCOUNT_PREFERENCE_KEYS)
        assertTrue(
            "ai_background_preview_summaries_daily_usage" in
                DEVICE_LOCAL_APP_PREFERENCE_KEYS
        )
        assertTrue(
            "ai_rule_evaluations_daily_usage" in
                DEVICE_LOCAL_APP_PREFERENCE_KEYS
        )
        assertTrue(
            "ai_rule_evaluation_run_status" in
                DEVICE_LOCAL_APP_PREFERENCE_KEYS
        )
        assertTrue("webdav_backup_enabled" in DEVICE_LOCAL_APP_PREFERENCE_KEYS)
        assertTrue("webdav_backup_directory_url" in DEVICE_LOCAL_APP_PREFERENCE_KEYS)
        assertTrue("webdav_backup_username" in DEVICE_LOCAL_APP_PREFERENCE_KEYS)
        assertTrue("webdav_backup_last_at" in DEVICE_LOCAL_APP_PREFERENCE_KEYS)
        assertTrue("webdav_backup_last_error" in DEVICE_LOCAL_APP_PREFERENCE_KEYS)
    }

    @Test
    fun `automatic backup and device transfer exclude encrypted preferences`() {
        assertEquals(
            listOf(
                "sharedpref" to "capy_secrets.xml",
                "sharedpref" to "capy_account_secrets.xml",
                "sharedpref" to "capy_device_state.xml",
            ),
            exclusions(R.xml.backup_rules),
        )
        assertEquals(
            listOf(
                "sharedpref" to "capy_secrets.xml",
                "sharedpref" to "capy_account_secrets.xml",
                "sharedpref" to "capy_device_state.xml",
                "sharedpref" to "capy_secrets.xml",
                "sharedpref" to "capy_account_secrets.xml",
                "sharedpref" to "capy_device_state.xml",
            ),
            exclusions(R.xml.data_extraction_rules),
        )
    }

    private fun exclusions(resourceID: Int): List<Pair<String?, String?>> {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resourceID)

        return buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "exclude"
                ) {
                    add(
                        parser.getAttributeValue(null, "domain") to
                            parser.getAttributeValue(null, "path")
                    )
                }
                parser.next()
            }
        }.also {
            parser.close()
        }
    }
}
