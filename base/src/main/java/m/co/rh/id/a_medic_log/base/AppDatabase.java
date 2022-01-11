package m.co.rh.id.a_medic_log.base;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_medic_log.base.entity.AndroidNotification;

@Database(entities = {AndroidNotification.class},
        version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AndroidNotificationDao androidNotificationDao();
}
