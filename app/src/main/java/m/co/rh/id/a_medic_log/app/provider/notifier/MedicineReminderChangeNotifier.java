package m.co.rh.id.a_medic_log.app.provider.notifier;

import java.io.Serializable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

public class MedicineReminderChangeNotifier {
    private Subject<MedicineReminder> mAddedMedicineReminderSubject;
    private Subject<MedicineReminderUpdatedEvent> mUpdatedMedicineReminderSubject;
    private Subject<MedicineReminder> mDeletedMedicineReminderSubject;

    public MedicineReminderChangeNotifier() {
        mAddedMedicineReminderSubject = PublishSubject.<MedicineReminder>create().toSerialized();
        mUpdatedMedicineReminderSubject = PublishSubject.<MedicineReminderUpdatedEvent>create().toSerialized();
        mDeletedMedicineReminderSubject = PublishSubject.<MedicineReminder>create().toSerialized();
    }

    public void medicineReminderAdded(MedicineReminder medicineReminder) {
        mAddedMedicineReminderSubject.onNext(medicineReminder);
    }

    public void medicineReminderUpdated(MedicineReminder beforeUpdate, MedicineReminder afterUpdate) {
        mUpdatedMedicineReminderSubject.onNext(new MedicineReminderUpdatedEvent(beforeUpdate, afterUpdate));
    }

    public void medicineReminderDeleted(MedicineReminder medicineReminder) {
        mDeletedMedicineReminderSubject.onNext(medicineReminder);
    }

    public Flowable<MedicineReminder> getAddedMedicineReminder() {
        return Flowable.fromObservable(mAddedMedicineReminderSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<MedicineReminderUpdatedEvent> getUpdatedMedicineReminder() {
        return Flowable.fromObservable(mUpdatedMedicineReminderSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<MedicineReminder> getDeletedMedicineReminder() {
        return Flowable.fromObservable(mDeletedMedicineReminderSubject, BackpressureStrategy.BUFFER);
    }

    public static class MedicineReminderUpdatedEvent implements Serializable {
        private MedicineReminder mBefore;
        private MedicineReminder mAfter;

        public MedicineReminderUpdatedEvent(MedicineReminder before, MedicineReminder after) {
            mBefore = before;
            mAfter = after;
        }

        public MedicineReminder getBefore() {
            return mBefore;
        }

        public MedicineReminder getAfter() {
            return mAfter;
        }
    }
}
