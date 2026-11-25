package dev.atoll.island.service

import android.graphics.drawable.Icon
import dev.atoll.island.R
import dev.atoll.island.data.model.SmallIconMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveAlertNotificationBuilderTest {

    private val appPkg = "dev.atoll.island"
    private val expectedTransparent = R.drawable.ic_transparent
    private val launcherIcon = R.mipmap.ic_launcher

    @Test
    fun `expectedSmallIconResId maps each mode to distinct drawable`() {
        assertEquals(R.drawable.ic_transparent, LiveAlertNotificationBuilder.expectedSmallIconResId(SmallIconMode.TRANSPARENT))
        assertEquals(R.drawable.ic_dot, LiveAlertNotificationBuilder.expectedSmallIconResId(SmallIconMode.DOT))
        assertEquals(R.drawable.ic_qs_atoll, LiveAlertNotificationBuilder.expectedSmallIconResId(SmallIconMode.CAPSULE))
    }

    @Test
    fun `ColorOS healthy notification keeps ic_transparent in oplus_small_icon even when standard icon becomes launcher icon`() {
        val mismatch = LiveAlertNotificationBuilder.isIconResourceMismatch(
            expectedIconResId = expectedTransparent,
            appPackageName = appPkg,
            oplusSmallIconType = Icon.TYPE_RESOURCE,
            oplusSmallIconPackage = appPkg,
            oplusSmallIconResId = expectedTransparent,
            oplusUsesAppIcon = true,
            standardSmallIconType = Icon.TYPE_RESOURCE,
            standardSmallIconPackage = appPkg,
            standardSmallIconResId = launcherIcon
        )
        assertFalse(mismatch)
    }

    @Test
    fun `ColorOS corrupted notification detects ic_launcher stored inside oplus_small_icon`() {
        val mismatch = LiveAlertNotificationBuilder.isIconResourceMismatch(
            expectedIconResId = expectedTransparent,
            appPackageName = appPkg,
            oplusSmallIconType = Icon.TYPE_RESOURCE,
            oplusSmallIconPackage = appPkg,
            oplusSmallIconResId = launcherIcon,
            oplusUsesAppIcon = true,
            standardSmallIconType = Icon.TYPE_RESOURCE,
            standardSmallIconPackage = appPkg,
            standardSmallIconResId = launcherIcon
        )
        assertTrue(mismatch)
    }

    @Test
    fun `ColorOS dropped oplus_small_icon while oplus_smallicon_use_app_icon is true triggers repair`() {
        val mismatch = LiveAlertNotificationBuilder.isIconResourceMismatch(
            expectedIconResId = expectedTransparent,
            appPackageName = appPkg,
            oplusSmallIconType = null,
            oplusSmallIconPackage = null,
            oplusSmallIconResId = null,
            oplusUsesAppIcon = true,
            standardSmallIconType = Icon.TYPE_RESOURCE,
            standardSmallIconPackage = appPkg,
            standardSmallIconResId = launcherIcon
        )
        assertTrue(mismatch)
    }

    @Test
    fun `ColorOS non-resource oplus_small_icon triggers repair`() {
        val mismatch = LiveAlertNotificationBuilder.isIconResourceMismatch(
            expectedIconResId = expectedTransparent,
            appPackageName = appPkg,
            oplusSmallIconType = Icon.TYPE_BITMAP,
            oplusSmallIconPackage = null,
            oplusSmallIconResId = null,
            oplusUsesAppIcon = true,
            standardSmallIconType = Icon.TYPE_RESOURCE,
            standardSmallIconPackage = appPkg,
            standardSmallIconResId = launcherIcon
        )
        assertTrue(mismatch)
    }

    @Test
    fun `standard Android healthy and mismatched notifications behave accurately`() {
        assertFalse(
            LiveAlertNotificationBuilder.isIconResourceMismatch(
                expectedIconResId = expectedTransparent,
                appPackageName = appPkg,
                oplusSmallIconType = null,
                oplusSmallIconPackage = null,
                oplusSmallIconResId = null,
                oplusUsesAppIcon = false,
                standardSmallIconType = Icon.TYPE_RESOURCE,
                standardSmallIconPackage = appPkg,
                standardSmallIconResId = expectedTransparent
            )
        )
        assertTrue(
            LiveAlertNotificationBuilder.isIconResourceMismatch(
                expectedIconResId = expectedTransparent,
                appPackageName = appPkg,
                oplusSmallIconType = null,
                oplusSmallIconPackage = null,
                oplusSmallIconResId = null,
                oplusUsesAppIcon = false,
                standardSmallIconType = Icon.TYPE_RESOURCE,
                standardSmallIconPackage = appPkg,
                standardSmallIconResId = launcherIcon
            )
        )
    }
}
