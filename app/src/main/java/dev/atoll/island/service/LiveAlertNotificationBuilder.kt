package dev.atoll.island.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import dev.atoll.island.R
import dev.atoll.island.data.model.AdaptivePaddingResult
import dev.atoll.island.data.model.DeviceDisplayMetrics
import dev.atoll.island.data.model.ExpandedCardMode
import dev.atoll.island.data.model.LiveAlertConfig
import dev.atoll.island.data.model.NotificationChannelMode
import dev.atoll.island.data.model.NotificationTemplateStyle
import dev.atoll.island.data.model.PromotionDiagnostics
import dev.atoll.island.data.model.SmallIconMode
import dev.atoll.island.domain.AdaptiveCapsuleSolver
import dev.atoll.island.domain.AndroidDisplayMetricsProvider
import dev.atoll.island.domain.CapsulePaddingGenerator
import dev.atoll.island.domain.card.CardContent
import dev.atoll.island.service.card.CardNotificationDecorator
import dev.atoll.island.service.card.ColorOsCardDecorator
import dev.atoll.island.ui.MainActivity

object LiveAlertNotificationBuilder {

    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    internal const val EXTRA_OPLUS_SMALL_ICON = "oplus_small_icon"
    internal const val EXTRA_OPLUS_SMALL_ICON_USE_APP_ICON = "oplus_smallicon_use_app_icon"
    internal const val CHANNEL_CUSTOM_ALERT_HIGH = "atoll_custom_alert_sound"
    internal const val CHANNEL_CUSTOM_ALERT_SILENT = "atoll_custom_alert_silent"
    private const val FLAG_PROMOTED_ONGOING_FALLBACK = 0x00040000

    // solo le build debug lo impostano, per provare extras di ColorOS da adb
    @Volatile
    internal var debugHook: ((Context, NotificationCompat.Builder) -> Unit)? = null

    @Volatile
    private var channelsEnsured = false

    private val flagPromotedOngoingMask: Int by lazy {
        runCatching {
            Notification::class.java.getField("FLAG_PROMOTED_ONGOING").getInt(null)
        }.getOrDefault(FLAG_PROMOTED_ONGOING_FALLBACK)
    }

    fun ensureChannels(context: Context) {
        if (channelsEnsured) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val lowChannel = NotificationChannel(
            NotificationChannelMode.LOW.channelId,
            context.getString(R.string.channel_low_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_low_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        val defaultChannel = NotificationChannel(
            NotificationChannelMode.DEFAULT.channelId,
            context.getString(R.string.channel_default_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_default_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        val customSoundChannel = NotificationChannel(
            CHANNEL_CUSTOM_ALERT_HIGH,
            context.getString(R.string.sidekey_custom_notif_channel_sound),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.sidekey_custom_notif_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }

        val customSilentChannel = NotificationChannel(
            CHANNEL_CUSTOM_ALERT_SILENT,
            context.getString(R.string.sidekey_custom_notif_channel_silent),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.sidekey_custom_notif_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(lowChannel)
        manager.createNotificationChannel(defaultChannel)
        manager.createNotificationChannel(customSoundChannel)
        manager.createNotificationChannel(customSilentChannel)
        channelsEnsured = true
    }

    fun solveForDevice(
        context: Context,
        config: LiveAlertConfig
    ): Pair<DeviceDisplayMetrics, AdaptivePaddingResult> {
        val metrics = AndroidDisplayMetricsProvider.readMetrics(
            context = context,
            persistedCutoutWidthPx = config.detectedCutoutWidthPx
        )
        val result = AdaptiveCapsuleSolver.solve(
            config = config,
            metrics = metrics
        )
        return metrics to result
    }

    fun resolveCard(context: Context, config: LiveAlertConfig): CardContent =
        CardNotificationDecorator.resolve(
            context,
            config,
            CapsulePaddingGenerator.titleFor(config.titleMode),
            CapsulePaddingGenerator.contentTextFor(config.titleMode)
        )

    fun activeStyleName(context: Context, config: LiveAlertConfig): String =
        if (config.card.mode == ExpandedCardMode.MINIMAL) {
            if (config.templateStyle == NotificationTemplateStyle.PROGRESS_STYLE) "ProgressStyle" else "Standard"
        } else {
            CardNotificationDecorator.styleName(resolveCard(context, config).style).let {
                if (config.card.useColorOsLayouts) "ColorOS + $it" else it
            }
        }

    fun buildSideKeyStandby(context: Context): Notification {
        ensureChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            901,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, NotificationChannelMode.LOW.channelId)
            .setSmallIcon(R.drawable.ic_qs_atoll)
            .setContentTitle(context.getString(R.string.sidekey_standby_notif_title))
            .setContentText(context.getString(R.string.sidekey_standby_notif_body))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setRequestPromotedOngoing(false)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun build(
        context: Context,
        config: LiveAlertConfig,
        solvedResult: AdaptivePaddingResult = solveForDevice(context, config).second,
        calibrationOverrideText: String? = null,
        cardContent: CardContent = resolveCard(context, config)
    ): Notification {
        ensureChannels(context)

        val iconRes = expectedSmallIconResId(config.smallIconMode)

        val rawText = calibrationOverrideText ?: solvedResult.text
        val paddingText = CapsulePaddingGenerator.applyDebugOverlay(
            text = rawText,
            debugVisibleMode = config.debugVisibleMode,
            debugGlyph = config.debugGlyph
        )
        val titleText = CapsulePaddingGenerator.titleFor(config.titleMode)
        val contentText = CapsulePaddingGenerator.contentTextFor(config.titleMode)

        val builder = NotificationCompat.Builder(context, config.channelMode.channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                }
            )
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (paddingText.isNotEmpty()) {
            builder.setShortCriticalText(paddingText)
        }

        // la scheda cambia solo il contenuto espanso, mai la pillola
        CardNotificationDecorator.apply(context, builder, config, cardContent)
        // la capsula di ColorOS riusa lo stesso testo, così la pillola resta larga uguale; in calibrazione si misura quella nativa
        if (calibrationOverrideText == null) {
            ColorOsCardDecorator.apply(context, builder, config, paddingText)
        }
        debugHook?.invoke(context, builder)

        if (config.card.mode == ExpandedCardMode.MINIMAL &&
            config.templateStyle == NotificationTemplateStyle.PROGRESS_STYLE
        ) {
            val progressStyle = NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
            if (config.progressIndeterminate) {
                progressStyle.setProgressIndeterminate(true)
            } else {
                progressStyle.setProgress(100)
                progressStyle.addProgressSegment(
                    NotificationCompat.ProgressStyle.Segment(100)
                )
            }
            builder.setStyle(progressStyle)
        }

        return builder.build()
    }

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nmCompat = NotificationManagerCompat.from(context)
        return runCatching { nmCompat.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun evaluateDiagnostics(
        context: Context,
        config: LiveAlertConfig,
        isServiceRunning: Boolean,
        solvedResult: AdaptivePaddingResult = solveForDevice(context, config).second,
        builtNotification: Notification? = null
    ): PromotionDiagnostics {
        val nm = context.getSystemService(NotificationManager::class.java)

        val isAndroid16Plus = Build.VERSION.SDK_INT >= 36
        val notificationsEnabled = nm?.areNotificationsEnabled() ?: false
        val canPostPromoted = canPostPromotedNotifications(context)

        val sampleNotification = builtNotification ?: runCatching { build(context, config, solvedResult) }.getOrNull()
        val eligible = if (sampleNotification != null && isAndroid16Plus) {
            runCatching {
                NotificationCompat.hasPromotableCharacteristics(sampleNotification)
            }.getOrDefault(false)
        } else {
            false
        }

        val activeSbn = runCatching {
            nm?.activeNotifications?.firstOrNull { it.id == AtollLiveAlertService.NOTIFICATION_ID }
        }.getOrNull()

        val activeFlags = activeSbn?.notification?.flags ?: 0
        val shownAsLiveAlert = activeSbn?.notification?.let(::isPromoted) == true

        val shortLen = activeSbn?.notification?.let { notif ->
            NotificationCompat.getShortCriticalText(notif)?.length
        } ?: CapsulePaddingGenerator.applyDebugOverlay(
            text = solvedResult.text,
            debugVisibleMode = config.debugVisibleMode,
            debugGlyph = config.debugGlyph
        ).length

        return PromotionDiagnostics(
            apiLevel = Build.VERSION.SDK_INT,
            isAndroid16Plus = isAndroid16Plus,
            notificationsEnabled = notificationsEnabled,
            canPostPromotedNotifications = canPostPromoted,
            builtNotificationEligible = eligible,
            isServiceRunning = isServiceRunning,
            shownAsLiveAlert = shownAsLiveAlert,
            activeNotificationFlags = activeFlags,
            shortCriticalTextLength = shortLen,
            activeStyle = activeStyleName(context, config)
        )
    }

    internal fun expectedSmallIconResId(mode: SmallIconMode): Int = when (mode) {
        SmallIconMode.TRANSPARENT -> R.drawable.ic_transparent
        SmallIconMode.DOT -> R.drawable.ic_dot
        SmallIconMode.CAPSULE -> R.drawable.ic_qs_atoll
    }

    fun isPromoted(notification: Notification): Boolean =
        (notification.flags and flagPromotedOngoingMask) != 0

    // a volte ColorOS mette l'icona dell'app al posto di oplus_small_icon
    fun needsRepost(
        notification: Notification?,
        config: LiveAlertConfig,
        appPackageName: String,
        requirePromotion: Boolean
    ): Boolean {
        if (notification == null) return true
        if (requirePromotion && !isPromoted(notification)) return true
        // con MetricStyle l'icona dell'app la mette ColorOS ogni volta: ripubblicare non serve
        if (notification.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MetricStyle") == true) return false
        val expectedResId = expectedSmallIconResId(config.smallIconMode)
        val oplusSmallIcon = runCatching {
            BundleCompat.getParcelable(notification.extras, EXTRA_OPLUS_SMALL_ICON, Icon::class.java)
        }.getOrNull()
        val oplusUsesAppIcon = notification.extras.getBoolean(EXTRA_OPLUS_SMALL_ICON_USE_APP_ICON, false)
        val standardIcon = notification.smallIcon

        return isIconResourceMismatch(
            expectedIconResId = expectedResId,
            appPackageName = appPackageName,
            oplusSmallIconType = oplusSmallIcon?.type,
            oplusSmallIconPackage = oplusSmallIcon.resPackageOrNull(),
            oplusSmallIconResId = oplusSmallIcon.resIdOrNull(),
            oplusUsesAppIcon = oplusUsesAppIcon,
            standardSmallIconType = standardIcon?.type,
            standardSmallIconPackage = standardIcon.resPackageOrNull(),
            standardSmallIconResId = standardIcon.resIdOrNull()
        )
    }

    internal fun isIconResourceMismatch(
        expectedIconResId: Int,
        appPackageName: String,
        oplusSmallIconType: Int?,
        oplusSmallIconPackage: String?,
        oplusSmallIconResId: Int?,
        oplusUsesAppIcon: Boolean,
        standardSmallIconType: Int?,
        standardSmallIconPackage: String?,
        standardSmallIconResId: Int?
    ): Boolean {
        if (oplusSmallIconType != null) {
            if (oplusSmallIconType != Icon.TYPE_RESOURCE) return true
            return oplusSmallIconPackage == appPackageName && oplusSmallIconResId != expectedIconResId
        }
        if (oplusUsesAppIcon) {
            return true
        }
        if (standardSmallIconType == Icon.TYPE_RESOURCE &&
            standardSmallIconPackage == appPackageName &&
            standardSmallIconResId != expectedIconResId
        ) {
            return true
        }
        return false
    }

    private fun Icon?.resPackageOrNull(): String? =
        if (this != null && type == Icon.TYPE_RESOURCE) resPackage else null

    private fun Icon?.resIdOrNull(): Int? =
        if (this != null && type == Icon.TYPE_RESOURCE) resId else null
}
