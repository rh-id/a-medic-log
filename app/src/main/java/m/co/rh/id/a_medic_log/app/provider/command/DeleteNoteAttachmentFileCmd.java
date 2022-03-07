package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteAttachmentFileChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.aprovider.Provider;

public class DeleteNoteAttachmentFileCmd {
    protected ExecutorService mExecutorService;
    protected NoteDao mNoteDao;
    protected NoteAttachmentFileChangeNotifier mNoteAttachmentFileChangeNotifier;

    public DeleteNoteAttachmentFileCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteDao = provider.get(NoteDao.class);
        mNoteAttachmentFileChangeNotifier = provider.get(NoteAttachmentFileChangeNotifier.class);
    }

    public Single<NoteAttachmentFile> execute(NoteAttachmentFile noteAttachmentFile) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mNoteDao.delete(noteAttachmentFile);
            mNoteAttachmentFileChangeNotifier.noteAttachmentFileDeleted(noteAttachmentFile);
            return noteAttachmentFile;
        }));
    }
}
