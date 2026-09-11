package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GetAdherenceReportCmd {
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<NoteDao> mNoteDao;
    private ProviderValue<ProfileDao> mProfileDao;

    public GetAdherenceReportCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
    }

    public Single<AdherenceReport> execute(long profileId, int rangeDays) {
        return Single.fromCallable(() -> {
            Date now = new Date();
            long from = AdherenceCalculator.rangeStart(now, rangeDays);
            long to = AdherenceCalculator.rangeEnd(now);
            List<MedicineIntake> intakesInRange = mMedicineIntakeDao.get()
                    .findMedicineIntakesByTakenDateTimeBetween(from, to);
            List<MedicineReminder> enabledReminders = mMedicineReminderDao.get()
                    .findMedicineRemindersEnabled();
            List<Medicine> medicines = mMedicineDao.get().findAllMedicines();
            List<Note> notes = mNoteDao.get().findAllNotes();
            List<Profile> profiles = mProfileDao.get().findProfiles();
            Map<Long, Medicine> medicineMap = new HashMap<>();
            for (Medicine medicine : medicines) {
                medicineMap.put(medicine.id, medicine);
            }
            Map<Long, Note> noteMap = new HashMap<>();
            for (Note note : notes) {
                noteMap.put(note.id, note);
            }
            Map<Long, Profile> profileMap = new HashMap<>();
            for (Profile profile : profiles) {
                profileMap.put(profile.id, profile);
            }
            // unknown profile (e.g. deleted in the meantime): report empty instead of failing
            if (!profileMap.containsKey(profileId)) {
                return new AdherenceReport();
            }
            return AdherenceCalculator.calculate(profileId, rangeDays, now,
                    intakesInRange, enabledReminders, medicineMap, noteMap, profileMap);
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
