package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.repository.NoteAttachmentRepository;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class DeleteNoteAttachmentCmd {
    protected ExecutorService mExecutorService;
    protected NoteAttachmentRepository mNoteAttachmentRepo;

    public DeleteNoteAttachmentCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteAttachmentRepo = provider.get(NoteAttachmentRepository.class);
    }

    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromCallable(() -> {
            mNoteAttachmentRepo.deleteNoteAttachment(noteAttachmentState);
            return noteAttachmentState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
