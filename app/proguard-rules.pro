-keepclassmembers class android.app.Notification {
    public boolean hasPromotableCharacteristics();
}
-keepclassmembers class android.app.NotificationManager {
    public boolean canPostPromotedNotifications();
}
