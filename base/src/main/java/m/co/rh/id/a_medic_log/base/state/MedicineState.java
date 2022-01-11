package m.co.rh.id.a_medic_log.base.state;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;

public class MedicineState implements Serializable, Cloneable {
    private transient BehaviorSubject<Medicine> mMedicineSubject;
    private transient BehaviorSubject<ArrayList<MedicineReminder>> mMedicineReminderListSubject;

    public MedicineState() {
        mMedicineSubject = BehaviorSubject.createDefault(new Medicine());
        mMedicineReminderListSubject = BehaviorSubject.createDefault(new ArrayList<>());
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
        return Flowable.fromObservable(mMedicineSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<MedicineReminder>> getMedicineReminderListFlow() {
        return Flowable.fromObservable(mMedicineReminderListSubject, BackpressureStrategy.BUFFER);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(mMedicineSubject.getValue());
        out.writeObject(mMedicineReminderListSubject.getValue());
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Medicine medicine = (Medicine) in.readObject();
        ArrayList<MedicineReminder> medicineReminders = (ArrayList<MedicineReminder>) in.readObject();
        mMedicineSubject = BehaviorSubject.createDefault(medicine);
        mMedicineReminderListSubject = BehaviorSubject.createDefault(medicineReminders);
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
