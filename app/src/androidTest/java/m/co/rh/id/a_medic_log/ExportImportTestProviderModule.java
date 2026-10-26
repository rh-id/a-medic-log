package m.co.rh.id.a_medic_log;

import androidx.room.Room;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import m.co.rh.id.a_medic_log.app.provider.CommandProviderModule;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderAlarmScheduler;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
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
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.provider.BaseProviderModule;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
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
 * The medicine reminder scheduler is replaced by
 * {@link RecordingMedicineReminderAlarmScheduler} which records the scheduled
 * trigger times per reminder id instead of touching the device AlarmManager.
 * <p>
 * The production WorkManager is never initialized in tests because the
 * androidx.startup InitializationProvider is removed from the test app manifest.
 * WorkManager.getInstance (resolved by MedicineReminderEventHandler for the
 * legacy reminder-work migration) therefore requires
 * WorkManagerTestInitHelper.initializeTestWorkManager to have run first.
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
        providerRegistry.registerAsync(MedicineDoseOccurrenceDao.class, () -> provider.get(AppDatabase.class)
                .medicineDoseOccurrenceDao());
        providerRegistry.registerAsync(ProfileChangeNotifier.class, ProfileChangeNotifier::new);
        providerRegistry.registerAsync(NoteChangeNotifier.class, NoteChangeNotifier::new);
        providerRegistry.registerAsync(MedicineChangeNotifier.class, MedicineChangeNotifier::new);
        providerRegistry.registerAsync(MedicineReminderChangeNotifier.class, MedicineReminderChangeNotifier::new);
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(provider.getContext()));
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider));
        // registered before MedicineReminderEventHandler, the handler resolves it lazily;
        // records alarm scheduling so tests can assert which reminders got an alarm
        providerRegistry.registerAsync(MedicineReminderAlarmScheduler.class,
                () -> new RecordingMedicineReminderAlarmScheduler(provider));
        // registered before MedicineReminderEventHandler, the handler resolves it in
        // its constructor (lazyGet validates the registration eagerly) to backfill
        // the dose occurrence table
        providerRegistry.registerLazy(MedicineRepository.class, () -> new MedicineRepository(provider));
        // subscribes to the change notifiers so reminder alarms are scheduled on imported note added events,
        // mirrors the production wiring of AppProviderModule
        providerRegistry.registerAsync(MedicineReminderEventHandler.class,
                () -> new MedicineReminderEventHandler(provider));
        // commands registered last, mirror the real app command wiring
        providerRegistry.registerModule(new CommandProviderModule());
    }

    /**
     * Test double of MedicineReminderAlarmScheduler that records every
     * scheduled trigger time per reminder id (including duplicates, so a
     * double-schedule regression is observable) and never touches the
     * device AlarmManager.
     */
    public static class RecordingMedicineReminderAlarmScheduler extends MedicineReminderAlarmScheduler {

        private final Map<Long, List<Long>> mScheduledTriggerTimes = new ConcurrentHashMap<>();

        public RecordingMedicineReminderAlarmScheduler(Provider provider) {
            super(provider);
        }

        @Override
        protected void scheduleInternal(MedicineReminder reminder, long triggerAt) {
            List<Long> triggerTimes = mScheduledTriggerTimes.get(reminder.id);
            if (triggerTimes == null) {
                triggerTimes = Collections.synchronizedList(new ArrayList<>());
                mScheduledTriggerTimes.put(reminder.id, triggerTimes);
            }
            triggerTimes.add(triggerAt);
        }

        @Override
        public void cancel(MedicineReminder reminder) {
            if (reminder != null && reminder.id != null) {
                mScheduledTriggerTimes.remove(reminder.id);
            }
        }

        public Map<Long, List<Long>> getScheduledTriggerTimes() {
            return mScheduledTriggerTimes;
        }
    }
}
