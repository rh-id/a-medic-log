package m.co.rh.id.a_medic_log.base.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.repository.AndroidNotificationRepo;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public DatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public DatabaseProviderModule() {
        mDbName = "a-medic-log.db";
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = context.getApplicationContext();
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(appContext,
                        AppDatabase.class, mDbName)
                        .addMigrations(DbMigration.getAll())
                        .build());
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(ProfileDao.class, () -> provider.get(AppDatabase.class)
                .profileDao());
        providerRegistry.registerAsync(NoteDao.class, () -> provider.get(AppDatabase.class)
                .noteDao());
        providerRegistry.registerAsync(MedicineDao.class, () -> provider.get(AppDatabase.class)
                .medicineDao());
        providerRegistry.registerAsync(AndroidNotificationDao.class, () -> provider.get(AppDatabase.class)
                .androidNotificationDao());
        providerRegistry.registerLazy(AndroidNotificationRepo.class, () ->
                new AndroidNotificationRepo(context, provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        mDbName = null;
    }
}
