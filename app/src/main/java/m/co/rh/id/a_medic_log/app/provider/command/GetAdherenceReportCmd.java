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
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GetAdherenceReportCmd {
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<MedicineDoseOccurrenceDao> mMedicineDoseOccurrenceDao;
    private ProviderValue<ProfileDao> mProfileDao;
    private ProviderValue<MedicineRepository> mMedicineRepository;

    public GetAdherenceReportCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineDoseOccurrenceDao = provider.lazyGet(MedicineDoseOccurrenceDao.class);
        mProfileDao = provider.lazyGet(ProfileDao.class);
        mMedicineRepository = provider.lazyGet(MedicineRepository.class);
    }

    public Single<AdherenceReport> execute(long profileId, int rangeDays) {
        return Single.fromCallable(() -> {
            Date now = new Date();
            // record any occurrences the reminder commands have not yet
            // generated (e.g. doses of the last hours since the last app run),
            // the catch-up's single encompassing transaction serializes this
            // with the reminder commands
            mMedicineRepository.get().catchUpEnabledRemindersByProfile(profileId, now);
            // unknown profile (e.g. deleted in the meantime): report empty instead of failing
            Profile profile = mProfileDao.get().findProfileById(profileId);
            if (profile == null) {
                return new AdherenceReport();
            }
            long from = AdherenceCalculator.rangeStart(now, rangeDays);
            long to = AdherenceCalculator.rangeEnd(now);
            List<Medicine> medicines = mMedicineDao.get().findMedicinesByProfileId(profileId);
            Map<Long, Medicine> medicineMap = new HashMap<>();
            for (Medicine medicine : medicines) {
                medicineMap.put(medicine.id, medicine);
            }
            List<MedicineDoseOccurrence> occurrencesInRange = mMedicineDoseOccurrenceDao.get()
                    .findMedicineDoseOccurrencesByScheduledDateTimeBetween(from, to);
            // both bounds are widened by the matching window: an intake logged
            // outside the range (e.g. 00:30 the next day) can still be the
            // matching intake of an occurrence inside it
            List<MedicineIntake> intakesInRangeWidened = mMedicineIntakeDao.get()
                    .findMedicineIntakesByTakenDateTimeBetween(
                            from - AdherenceCalculator.WINDOW_MILLIS,
                            to + AdherenceCalculator.WINDOW_MILLIS);
            return AdherenceCalculator.calculate(profileId, rangeDays, now,
                    occurrencesInRange, intakesInRangeWidened, medicineMap, profile);
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }
}
