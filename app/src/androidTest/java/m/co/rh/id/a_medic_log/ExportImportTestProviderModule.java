package m.co.rh.id.a_medic_log;

import androidx.room.Room;
import androidx.work.WorkManager;

import m.co.rh.id.a_medic_log.app.provider.CommandProviderModule;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
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
 * Provider module for the export/import instrumented tests,
 * provides an in-memory database surrounded by the real app components
 * (executor, logger, FileHelper and change notifiers).
 * <p>
 * Anything resolving WorkManager.getInstance (e.g. MedicineReminderEventHandler)
 * requires WorkManagerTestInitHelper.initializeTestWorkManager to have run first:
 * the production WorkManager is never initialized in tests because the
 * androidx.startup InitializationProvider is removed from the test app manifest.
 */
public class ExportImportTestProviderModule implements ProviderModule {

    /**
     * Decorator applied to the in-memory AppDatabase, lets a test wrap the database
     * (e.g. to force a transaction failure). Identity by default.
     */
    public interface AppDatabaseDecorator {
        AppDatabase decorate(AppDatabase appDatabase);
    }

    private final AppDatabaseDecorator mAppDatabaseDecorator;

    public ExportImportTestProviderModule() {
        this(appDatabase -> appDatabase);
    }

    public ExportImportTestProviderModule(AppDatabaseDecorator appDatabaseDecorator) {
        mAppDatabaseDecorator = appDatabaseDecorator;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerAsync(AppDatabase.class, () -> mAppDatabaseDecorator.decorate(
                Room.inMemoryDatabaseBuilder(provider.getContext(), AppDatabase.class)
                        .addTypeConverter(new LinkedHashSetConverter(provider.get(ILogger.class)))
                        .build()));
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
        providerRegistry.registerAsync(ProfileChangeNotifier.class, ProfileChangeNotifier::new);
        providerRegistry.registerAsync(NoteChangeNotifier.class, NoteChangeNotifier::new);
        providerRegistry.registerAsync(MedicineChangeNotifier.class, MedicineChangeNotifier::new);
        providerRegistry.registerAsync(MedicineReminderChangeNotifier.class, MedicineReminderChangeNotifier::new);
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(provider.getContext()));
        // subscribes to the change notifiers so reminder works are scheduled on imported note added events,
        // mirrors the production wiring of AppProviderModule
        providerRegistry.registerAsync(MedicineReminderEventHandler.class,
                () -> new MedicineReminderEventHandler(provider));
        // commands registered last, mirror the real app command wiring
        providerRegistry.registerModule(new CommandProviderModule());
    }
}
