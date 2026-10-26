package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDoseOccurrenceDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GetTodayDoseStatusCmd {
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<MedicineDoseOccurrenceDao> mMedicineDoseOccurrenceDao;
    private ProviderValue<NoteDao> mNoteDao;
    private ProviderValue<ProfileDao> mProfileDao;
    private ProviderValue<MedicineRepository> mMedicineRepository;

    public GetTodayDoseStatusCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineDoseOccurrenceDao = provider.lazyGet(MedicineDoseOccurrenceDao.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mMedicineRepository = provider.lazyGet(MedicineRepository.class);
    }

    public Single<List<TodayStatus>> execute() {
        return Single.fromCallable(() -> {
            Date now = new Date();
            // record any occurrences the reminder commands have not yet
            // generated (e.g. doses of the last hours since the last app run),
            // Today cards span profiles so this covers ALL enabled reminders;
            // the catch-up's single encompassing transaction serializes this
            // with the reminder commands
            mMedicineRepository.get().catchUpAllEnabled(now);
            // the window around today lets intakes logged shortly past midnight
            // match yesterday's late occurrences and vice versa
            long from = AdherenceCalculator.rangeStart(now, 1)
                    - AdherenceCalculator.WINDOW_MILLIS;
            long to = AdherenceCalculator.rangeEnd(now)
                    + AdherenceCalculator.WINDOW_MILLIS;
            List<MedicineDoseOccurrence> occurrencesToday = mMedicineDoseOccurrenceDao.get()
                    .findMedicineDoseOccurrencesByScheduledDateTimeBetween(from, to);
            List<MedicineIntake> intakesToday = mMedicineIntakeDao.get()
                    .findMedicineIntakesByTakenDateTimeBetween(from, to);
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
            return TodayCalculator.compute(now, occurrencesToday, intakesToday,
                    medicineMap, noteMap, profiles);
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
