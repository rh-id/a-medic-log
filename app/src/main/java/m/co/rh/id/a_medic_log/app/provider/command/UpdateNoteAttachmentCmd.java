package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.aprovider.Provider;

public class UpdateNoteAttachmentCmd extends NewNoteAttachmentCmd {

    public UpdateNoteAttachmentCmd(Context context, Provider provider) {
        super(context, provider);
    }

    @Override
    public Single<NoteAttachmentState> execute(NoteAttachmentState noteAttachmentState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mNoteDao.update(noteAttachmentState.getNoteAttachment());
            return noteAttachmentState;
        }));
    }
}
