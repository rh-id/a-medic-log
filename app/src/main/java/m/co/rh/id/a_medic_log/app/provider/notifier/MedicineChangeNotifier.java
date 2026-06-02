package m.co.rh.id.a_medic_log.app.provider.notifier;

import java.io.Serializable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_medic_log.base.state.MedicineState;

public class MedicineChangeNotifier {
    private Subject<MedicineState> mAddedMedicineSubject;
    private Subject<MedicineUpdatedEvent> mUpdatedMedicineSubject;
    private Subject<MedicineState> mDeletedMedicineSubject;

    public MedicineChangeNotifier() {
        mAddedMedicineSubject = PublishSubject.<MedicineState>create().toSerialized();
        mUpdatedMedicineSubject = PublishSubject.<MedicineUpdatedEvent>create().toSerialized();
        mDeletedMedicineSubject = PublishSubject.<MedicineState>create().toSerialized();
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
