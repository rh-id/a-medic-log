package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class QueryNoteCmd {
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<NoteDao> mNoteDao;
    protected ProviderValue<MedicineDao> mMedicineDao;

    public QueryNoteCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNoteDao = provider.lazyGet(NoteDao.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
    }

    public Single<NoteState> queryNoteInfo(long noteId) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            Note note = mNoteDao.get().findNoteById(noteId);
            NoteState noteState = new NoteState();
            noteState.updateNote(note);
            noteState = queryNoteInfo(noteState).blockingGet();
            return noteState;
        }));
    }

    public Single<NoteState> queryNoteInfo(NoteState noteState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            Long noteId = noteState.getNoteId();
            if (noteId == null) return noteState;
            Note note = mNoteDao.get().findNoteById(noteId);
            noteState.updateNote(note);
            queryMedicineStateList(noteState);
            return noteState;
        }));
    }

    private void queryMedicineStateList(NoteState noteState) {
        Long noteId = noteState.getNoteId();
        List<Medicine> medicineList = mMedicineDao.get().findMedicinesByNoteId(noteId);
        if (!medicineList.isEmpty()) {
            ArrayList<MedicineState> medicineStates = new ArrayList<>();
            for (Medicine medicine : medicineList) {
                List<MedicineReminder> medicineReminders = mMedicineDao.get().findMedicineRemindersByMedicineId(medicine.id);
                MedicineState medicineState = new MedicineState();
                medicineState.updateMedicine(medicine);
                medicineState.updateMedicineReminderList(medicineReminders);
                medicineStates.add(medicineState);
            }
            noteState.updateMedicineStates(medicineStates);
        }
    }

    public Single<List<MedicineState>> queryMedicineInfo(NoteState noteState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            queryMedicineStateList(noteState);
            return noteState.getMedicineList();
        }));
    }
}
