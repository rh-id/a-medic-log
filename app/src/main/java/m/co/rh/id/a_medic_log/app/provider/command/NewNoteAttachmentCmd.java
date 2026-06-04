package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.base.repository.NoteAttachmentRepository;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachment;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class NewNoteAttachmentCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected NoteAttachmentRepository mNoteAttachmentRepo;
    protected BehaviorSubject<String> mNameValidSubject;

    public NewNoteAttachmentCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNoteAttachmentRepo = provider.get(NoteAttachmentRepository.class);
        mNameValidSubject = BehaviorSubject.create();
    }

    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromCallable(() -> {
            mNoteAttachmentRepo.insertNoteAttachment(noteAttachmentState);
            return noteAttachmentState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(NoteAttachmentState noteAttachmentState) {
        boolean isValid = false;
        if (noteAttachmentState != null) {
            NoteAttachment noteAttachment = noteAttachmentState.getNoteAttachment();
            boolean nameValid;
            if (noteAttachment.name != null && !noteAttachment.name.isEmpty()) {
                nameValid = true;
                mNameValidSubject.onNext("");
            } else {
                nameValid = false;
                mNameValidSubject.onNext(mAppContext.getString(R.string.name_is_required));
            }
            isValid = nameValid;
        }
        return isValid;
    }

    public Flowable<String> getNameValid() {
        return Flowable.fromObservable(mNameValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String nameValid = mNameValidSubject.getValue();
        if (nameValid != null && !nameValid.isEmpty()) {
            return nameValid;
        }
        return "";
    }
}
