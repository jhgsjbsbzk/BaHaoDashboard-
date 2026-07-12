package com.cycling.dashboard.service

import android.service.notification.NotificationListenerService

/**
 * 媒体通知监听服务。
 * 用户在系统里开启“通知使用权”后，应用才能读取当前正在播放的音乐会话。
 */
class BaHaoMediaNotificationListener : NotificationListenerService()
