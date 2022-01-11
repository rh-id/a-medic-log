package m.co.rh.id.a_medic_log.app.provider.notifier;

import java.io.Serializable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;

public class MedicineIntakeChangeNotifier {
    private PublishSubject<MedicineIntake> mAddedMedicineSubject;
    private PublishSubject<MedicineIntakeUpdatedEvent> mUpdatedMedicineSubject;
    private PublishSubject<MedicineIntake> mDeletedMedicineSubject;

    public MedicineIntakeChangeNotifier() {
        mAddedMedicineSubject = PublishSubject.create();
        mUpdatedMedicineSubject = PublishSubject.create();
        mDeletedMedicineSubject = PublishSubject.create();
    }

    public void medicineIntakeAdded(MedicineIntake medicineIntake) {
        mAddedMedicineSubject.onNext(medicineIntake);
    }

    public Flowable<MedicineIntake> getAddedMedicineIntake() {
        return Flowable.fromObservable(mAddedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public void medicineIntakeUpdated(MedicineIntake before, MedicineIntake after) {
        mUpdatedMedicineSubject.onNext(new MedicineIntakeUpdatedEvent(before, after));
    }

    public Flowable<MedicineIntakeUpdatedEvent> getUpdatedMedicineIntake() {
        return Flowable.fromObservable(mUpdatedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public void medicineIntakeDeleted(MedicineIntake medicineIntake) {
        mDeletedMedicineSubject.onNext(medicineIntake);
    }

    public Flowable<MedicineIntake> getDeletedMedicineIntake() {
        return Flowable.fromObservable(mDeletedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public static class MedicineIntakeUpdatedEvent implements Serializable {
        private MedicineIntake mBefore;
        private MedicineIntake mAfter;

        public MedicineIntakeUpdatedEvent(MedicineIntake before, MedicineIntake after) {
            mBefore = before;
            mAfter = after;
        }

        public MedicineIntake getBefore() {
            return mBefore;
        }

        public MedicineIntake getAfter() {
            return mAfter;
        }
    }
}
