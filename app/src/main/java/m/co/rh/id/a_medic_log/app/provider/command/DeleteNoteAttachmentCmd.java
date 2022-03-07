package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class DeleteNoteAttachmentCmd {
    protected ExecutorService mExecutorService;
    protected NoteDao mNoteDao;

    public DeleteNoteAttachmentCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteDao = provider.get(NoteDao.class);
    }

    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mNoteDao.deleteNoteAttachment(noteAttachmentState);
            return noteAttachmentState;
        }));
    }
}
