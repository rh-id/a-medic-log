package m.co.rh.id.a_medic_log.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class UpdateNoteAttachmentCmd extends NewNoteAttachmentCmd {

    public UpdateNoteAttachmentCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mNoteDao.update(noteAttachmentState.getNoteAttachment());
            return noteAttachmentState;
        }));
    }
}
