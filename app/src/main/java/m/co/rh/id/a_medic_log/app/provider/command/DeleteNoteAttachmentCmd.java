package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.base.repository.NoteRepository;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteNoteAttachmentCmd {
    protected ExecutorService mExecutorService;
    protected ProviderValue<NoteRepository> mNoteRepository;

    public DeleteNoteAttachmentCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteRepository = provider.lazyGet(NoteRepository.class);
    }

    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromCallable(() -> {
            mNoteRepository.get().deleteNoteAttachment(noteAttachmentState);
            return noteAttachmentState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
