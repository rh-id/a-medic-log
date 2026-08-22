package m.co.rh.id.a_medic_log.app.provider.component;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_medic_log.app.receiver.NotificationDisableMedicineReminderReceiver;
import m.co.rh.id.a_medic_log.app.receiver.NotificationTakeMedicineReceiver;
import m.co.rh.id.a_medic_log.base.entity.AndroidNotification;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

public class MedicineReminderNotificationBuilder {

    public static final String GROUP_KEY_MEDICINE_REMINDER = "GROUP_KEY_MEDICINE_REMINDER";
    public static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";
    private static final String CHANNEL_ID_MEDICINE_REMINDER = "CHANNEL_ID_MEDICINE_REMINDER";

    private final Context mAppContext;

    public MedicineReminderNotificationBuilder(Context appContext) {
        mAppContext = appContext;
    }

    public AndroidNotification createAndroidNotification(MedicineReminder medicineReminder) {
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_MEDICINE_REMINDER;
        androidNotification.refId = medicineReminder.id;
        return androidNotification;
    }

    @RequiresPermission("android.permission.POST_NOTIFICATIONS")
    public void createMedicineReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_channel_name_medicine_reminder);
            String description = mAppContext.getString(R.string.notification_channel_description_medicine_reminder);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_MEDICINE_REMINDER,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = mAppContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public Notification buildMedicineReminderNotification(String title, String content, int requestId) {
        int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlag = PendingIntent.FLAG_IMMUTABLE;
        }
        Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
        receiverIntent.putExtra(KEY_INT_REQUEST_ID, requestId);
        PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, requestId, receiverIntent,
                intentFlag);
        Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
        deleteIntent.putExtra(KEY_INT_REQUEST_ID, requestId);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, requestId, deleteIntent,
                intentFlag);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_MEDICINE_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setColorized(true)
                .setColor(mAppContext.getResources().getColor(R.color.indigo_500))
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_MEDICINE_REMINDER)
                .setAutoCancel(false);
        Intent intentTakeMedicine = new Intent(mAppContext, NotificationTakeMedicineReceiver.class);
        intentTakeMedicine.putExtra(KEY_INT_REQUEST_ID, requestId);
        PendingIntent pendingIntentTakeMedicine = PendingIntent.getBroadcast(mAppContext, requestId, intentTakeMedicine,
                intentFlag);
        builder.addAction(R.drawable.ic_check_black, mAppContext.getString(R.string.take_medicine), pendingIntentTakeMedicine);
        Intent intentDisableMedicineReminder = new Intent(mAppContext, NotificationDisableMedicineReminderReceiver.class);
        intentDisableMedicineReminder.putExtra(KEY_INT_REQUEST_ID, requestId);
        PendingIntent pendingIntentDisableMedicineReminder = PendingIntent.getBroadcast(mAppContext, requestId, intentDisableMedicineReminder,
                intentFlag);
        builder.addAction(R.drawable.ic_timer_off_black, mAppContext.getString(R.string.disable_reminder), pendingIntentDisableMedicineReminder);
        return builder.build();
    }
}
