package com.gyf.cactus.ext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gyf.cactus.Cactus
import com.gyf.cactus.R
import com.gyf.cactus.entity.NotificationConfig
import com.gyf.cactus.exception.CactusException
import com.gyf.cactus.service.HideForegroundService

/**
 * @author geyifeng
 * @date 2019-11-16 18:01
 */

/**
 * 是否已经有Notification
 */
private val mHasNotification = mutableMapOf<String, Boolean>()

/**
 * 小图标
 */
private val NotificationConfig.handleSmallIcon
    get() = if (hideNotification && Build.VERSION.SDK_INT != Build.VERSION_CODES.N_MR1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !hideNotificationAfterO) {
            smallIcon
        } else {
            R.drawable.icon_cactus_trans
        }
    } else {
        smallIcon
    }

/**
 * 设置通知栏信息
 * @receiver Service
 * @param notificationConfig NotificationConfig
 */
internal fun Service.setNotification(
    notificationConfig: NotificationConfig,
    isHideService: Boolean = false
) {
    val tag = Cactus.CACTUS_TAG + System.identityHashCode(this)
    val hasNotification = mHasNotification[tag]
    if (hasNotification == null || !hasNotification) {
        mHasNotification[tag] = true
        val managerCompat = NotificationManagerCompat.from(this)
        val notification = getNotification(notificationConfig)
        notificationConfig.apply {
            //更新Notification
            managerCompat.notify(serviceId, notification)
            //设置前台服务Notification
            startForeground(serviceId, notification)
            //隐藏Notification
            if (hideNotification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (managerCompat.getNotificationChannel(notification.channelId) != null
                        && hideNotificationAfterO
                    ) {
                        sMainHandler.post { managerCompat.deleteNotificationChannel(notification.channelId) }
                    }
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
                    if (!isHideService) {
                        val intent = Intent(this@setNotification, HideForegroundService::class.java)
                        intent.putExtra(Cactus.CACTUS_NOTIFICATION_CONFIG, this)
                        startInternService(intent)
                    }
                }
            }
        }
    }
}


/**
 * 获得Notification
 *
 * @param notificationConfig NotificationConfig
 * @return Notification
 */
private fun Context.getNotification(notificationConfig: NotificationConfig): Notification =
    notificationConfig.run {
        val managerCompat = NotificationManagerCompat.from(this@getNotification)
        //构建Notification
        val notification =
            notification ?: NotificationCompat.Builder(this@getNotification, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(handleSmallIcon)
                .setLargeIcon(
                    largeIconBitmap ?: BitmapFactory.decodeResource(
                        resources,
                        largeIcon
                    )
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .apply {
                    remoteViews?.also {
                        setContent(it)
                    }
                    bigRemoteViews?.also {
                        setCustomBigContentView(it)
                    }
                }
                .build()
        //设置渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            managerCompat.createNotificationChannel(
                if (notificationChannel is NotificationChannel) {
                    (notificationChannel as NotificationChannel?)?.apply {
                        if (id != notification.channelId) {
                            throw CactusException(
                                "保证渠道相同(The id of the NotificationChannel " +
                                        "is different from the channel of the Notification.)"
                            )
                        }
                    }
                    notificationChannel as NotificationChannel?
                } else {
                    null
                } ?: NotificationChannel(
                    notification.channelId,
                    notificationConfig.channelName,
                    NotificationManager.IMPORTANCE_NONE
                )
            )
        }
        notification
    }