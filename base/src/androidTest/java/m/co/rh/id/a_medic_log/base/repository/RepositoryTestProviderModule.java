package m.co.rh.id.a_medic_log.base.repository;

import androidx.room.Room;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDoseOccurrenceDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentDao;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.NoteTagDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.provider.BaseProviderModule;
import m.co.rh.id.a_medic_log.base.room.converter.LinkedHashSetConverter;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for the repository instrumented tests, provides an
 * in-memory database surrounded by the real base components (executor,
 * logger, FileHelper). The whole graph is released with provider.dispose().
 * <p>
 * Change notifiers, WorkManager and commands are not registered since the
 * repositories never depend on them (the app module registers those around
 * the repositories instead).
 */
class RepositoryTestProviderModule implements ProviderModule {

    private volatile AppDatabase mAppDatabaseForDispose;

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerAsync(AppDatabase.class, () -> {
            AppDatabase appDatabase =
                    Room.inMemoryDatabaseBuilder(provider.getContext(), AppDatabase.class)
                            .addTypeConverter(new LinkedHashSetConverter(provider.get(ILogger.class)))
                            .build();
            mAppDatabaseForDispose = appDatabase;
            return appDatabase;
        });
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(ProfileDao.class, () -> provider.get(AppDatabase.class)
                .profileDao());
        providerRegistry.registerAsync(NoteDao.class, () -> provider.get(AppDatabase.class)
                .noteDao());
        providerRegistry.registerAsync(MedicineDao.class, () -> provider.get(AppDatabase.class)
                .medicineDao());
        providerRegistry.registerAsync(NoteTagDao.class, () -> provider.get(AppDatabase.class)
                .noteTagDao());
        providerRegistry.registerAsync(NoteAttachmentDao.class, () -> provider.get(AppDatabase.class)
                .noteAttachmentDao());
        providerRegistry.registerAsync(NoteAttachmentFileDao.class, () -> provider.get(AppDatabase.class)
                .noteAttachmentFileDao());
        providerRegistry.registerAsync(MedicineReminderDao.class, () -> provider.get(AppDatabase.class)
                .medicineReminderDao());
        providerRegistry.registerAsync(MedicineIntakeDao.class, () -> provider.get(AppDatabase.class)
                .medicineIntakeDao());
        providerRegistry.registerAsync(MedicineDoseOccurrenceDao.class, () -> provider.get(AppDatabase.class)
                .medicineDoseOccurrenceDao());
        // repositories, mirrors the DatabaseProviderModule registration
        providerRegistry.registerLazy(NoteRepository.class, () -> new NoteRepository(provider));
        providerRegistry.registerLazy(NoteAttachmentRepository.class, () -> new NoteAttachmentRepository(provider));
        providerRegistry.registerLazy(MedicineRepository.class, () -> new MedicineRepository(provider));
        providerRegistry.registerLazy(ProfileRepository.class, () -> new ProfileRepository(provider));
    }

    @Override
    public void dispose(Provider provider) {
        AppDatabase appDatabase = mAppDatabaseForDispose;
        if (appDatabase != null) {
            appDatabase.close();
        }
    }
}
