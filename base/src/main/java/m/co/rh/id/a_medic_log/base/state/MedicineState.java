package m.co.rh.id.a_medic_log.base.state;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

public class MedicineState implements Serializable, Cloneable {
    private SerialBehaviorSubject<Medicine> mMedicineSubject;
    private SerialBehaviorSubject<ArrayList<MedicineReminder>> mMedicineReminderListSubject;

    public MedicineState() {
        mMedicineSubject = new SerialBehaviorSubject<>(new Medicine());
        mMedicineReminderListSubject = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void setNoteId(long noteId) {
        mMedicineSubject.getValue().noteId = noteId;
    }

    public void setMedicineName(String name) {
        mMedicineSubject.getValue().name = name;
    }

    public void setMedicineDescription(String description) {
        mMedicineSubject.getValue().description = description;
    }

    public void updateMedicine(Medicine medicine) {
        mMedicineSubject.onNext(medicine);
    }

    public Medicine getMedicine() {
        return mMedicineSubject.getValue();
    }

    public void updateMedicineReminderList(List<MedicineReminder> medicineReminderList) {
        mMedicineReminderListSubject.onNext(new ArrayList<>(medicineReminderList));
    }

    public ArrayList<MedicineReminder> getMedicineReminderList() {
        return mMedicineReminderListSubject.getValue();
    }

    public Flowable<Medicine> getMedicineFlow() {
        return Flowable.fromObservable(mMedicineSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<MedicineReminder>> getMedicineReminderListFlow() {
        return Flowable.fromObservable(mMedicineReminderListSubject.getSubject(), BackpressureStrategy.BUFFER);
    }

    public String getMedicineName() {
        return getMedicine().name;
    }

    public String getMedicineDescription() {
        return getMedicine().description;
    }

    @Override
    public MedicineState clone() {
        MedicineState medicineState = new MedicineState();
        Medicine medicine = mMedicineSubject.getValue();
        if (medicine != null) {
            medicine = medicine.clone();
        }
        medicineState.updateMedicine(medicine);
        ArrayList<MedicineReminder> medicineReminders = mMedicineReminderListSubject.getValue();
        if (medicineReminders != null) {
            ArrayList<MedicineReminder> clonedList = new ArrayList<>(medicineReminders.size());
            for (MedicineReminder medicineReminder : medicineReminders) {
                clonedList.add(medicineReminder.clone());
            }
            medicineReminders = clonedList;
        } else {
            medicineReminders = new ArrayList<>();
        }
        medicineState.updateMedicineReminderList(medicineReminders);
        return medicineState;
    }

    public Long getMedicineId() {
        return getMedicine().id;
    }

    public void addMedicineReminder(MedicineReminder medicineReminder) {
        int index = findMedicineReminderIndex(medicineReminder);
        if (index == -1) {
            ArrayList<MedicineReminder> medicineReminders = getMedicineReminderList();
            medicineReminders.add(medicineReminder);
            mMedicineReminderListSubject.onNext(medicineReminders);
        }
    }

    public void updateMedicineReminder(MedicineReminder medicineReminder) {
        int index = findMedicineReminderIndex(medicineReminder);
        if (index != -1) {
            ArrayList<MedicineReminder> medicineReminders = getMedicineReminderList();
            medicineReminders.remove(index);
            medicineReminders.add(index, medicineReminder);
            mMedicineReminderListSubject.onNext(medicineReminders);
        }
    }

    private int findMedicineReminderIndex(MedicineReminder medicineReminder) {
        ArrayList<MedicineReminder> medicineReminders = getMedicineReminderList();
        int index = -1;
        int size = medicineReminders.size();
        for (int i = 0; i < size; i++) {
            MedicineReminder fromList = medicineReminders.get(i);
            if (fromList.id != null && fromList.id.equals(medicineReminder.id)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public void deleteMedicineReminder(MedicineReminder medicineReminder) {
        int index = findMedicineReminderIndex(medicineReminder);
        if (index != -1) {
            ArrayList<MedicineReminder> medicineReminders = getMedicineReminderList();
            medicineReminders.remove(index);
            mMedicineReminderListSubject.onNext(medicineReminders);
        }
    }
}
