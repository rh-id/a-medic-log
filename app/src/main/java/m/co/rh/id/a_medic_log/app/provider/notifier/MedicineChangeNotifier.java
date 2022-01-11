package m.co.rh.id.a_medic_log.app.provider.notifier;

import java.io.Serializable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.base.state.MedicineState;

public class MedicineChangeNotifier {
    private PublishSubject<MedicineState> mAddedMedicineSubject;
    private PublishSubject<MedicineUpdatedEvent> mUpdatedMedicineSubject;
    private PublishSubject<MedicineState> mDeletedMedicineSubject;

    public MedicineChangeNotifier() {
        mAddedMedicineSubject = PublishSubject.create();
        mUpdatedMedicineSubject = PublishSubject.create();
        mDeletedMedicineSubject = PublishSubject.create();
    }

    public void medicineAdded(MedicineState medicineState) {
        mAddedMedicineSubject.onNext(medicineState);
    }

    public void medicineUpdated(MedicineState beforeUpdate, MedicineState afterUpdate) {
        mUpdatedMedicineSubject.onNext(new MedicineUpdatedEvent(beforeUpdate, afterUpdate));
    }

    public void medicineDeleted(MedicineState medicineState) {
        mDeletedMedicineSubject.onNext(medicineState);
    }

    public Flowable<MedicineState> getAddedMedicine() {
        return Flowable.fromObservable(mAddedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<MedicineUpdatedEvent> getUpdatedMedicine() {
        return Flowable.fromObservable(mUpdatedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<MedicineState> getDeletedMedicine() {
        return Flowable.fromObservable(mDeletedMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public static class MedicineUpdatedEvent implements Serializable {
        private MedicineState mBefore;
        private MedicineState mAfter;

        public MedicineUpdatedEvent(MedicineState before, MedicineState after) {
            mBefore = before;
            mAfter = after;
        }

        public MedicineState getBefore() {
            return mBefore;
        }

        public MedicineState getAfter() {
            return mAfter;
        }
    }
}
