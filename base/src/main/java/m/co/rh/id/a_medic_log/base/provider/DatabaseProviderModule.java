package m.co.rh.id.a_medic_log.base.provider;

import androidx.room.Room;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.repository.AndroidNotificationRepository;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.a_medic_log.base.repository.NoteAttachmentRepository;
import m.co.rh.id.a_medic_log.base.repository.NoteRepository;
import m.co.rh.id.a_medic_log.base.repository.ProfileRepository;
import m.co.rh.id.a_medic_log.base.room.DbMigration;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;
import m.co.rh.id.alogger.ILogger;
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
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(provider.getContext(),
                        AppDatabase.class, mDbName)
                        .addMigrations(DbMigration.getAll())
                        .addTypeConverter(new LinkedHashSetConverter(provider.get(ILogger.class)))
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
        providerRegistry.registerAsync(NoteAttachmentDao.class, () -> provider.get(AppDatabase.class)
                .noteAttachmentDao());
        providerRegistry.registerAsync(NoteTagDao.class, () -> provider.get(AppDatabase.class)
                .noteTagDao());
        providerRegistry.registerAsync(NoteAttachmentFileDao.class, () -> provider.get(AppDatabase.class)
                .noteAttachmentFileDao());
        providerRegistry.registerAsync(MedicineReminderDao.class, () -> provider.get(AppDatabase.class)
                .medicineReminderDao());
        providerRegistry.registerAsync(MedicineIntakeDao.class, () -> provider.get(AppDatabase.class)
                .medicineIntakeDao());
        providerRegistry.registerLazy(AndroidNotificationRepository.class, () ->
                new AndroidNotificationRepository(provider));
        providerRegistry.registerLazy(NoteRepository.class, () ->
                new NoteRepository(provider));
        providerRegistry.registerLazy(NoteAttachmentRepository.class, () ->
                new NoteAttachmentRepository(provider));
        providerRegistry.registerLazy(MedicineRepository.class, () ->
                new MedicineRepository(provider));
        providerRegistry.registerLazy(ProfileRepository.class, () ->
                new ProfileRepository(provider));
    }
}
