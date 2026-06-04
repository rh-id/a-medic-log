package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteAttachmentFileChangeNotifier;
import m.co.rh.id.a_medic_log.base.dao.NoteAttachmentFileDao;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.aprovider.Provider;

public class NewNoteAttachmentFileCmd {
    protected ExecutorService mExecutorService;
    protected NoteAttachmentFileDao mNoteAttachmentFileDao;
    protected NoteAttachmentFileChangeNotifier mNoteAttachmentFileChangeNotifier;

    public NewNoteAttachmentFileCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteAttachmentFileDao = provider.get(NoteAttachmentFileDao.class);
        mNoteAttachmentFileChangeNotifier = provider.get(NoteAttachmentFileChangeNotifier.class);
    }

    public Single<NoteAttachmentFile> execute(NoteAttachmentFile noteAttachmentFile) {
        return Single.fromCallable(() -> {
            mNoteAttachmentFileDao.insert(noteAttachmentFile);
            mNoteAttachmentFileChangeNotifier.noteAttachmentFileAdded(noteAttachmentFile);
            return noteAttachmentFile;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
