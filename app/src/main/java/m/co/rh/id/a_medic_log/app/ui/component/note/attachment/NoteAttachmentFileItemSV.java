package m.co.rh.id.a_medic_log.app.ui.component.note.attachment;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.common.ImageViewPage;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class NoteAttachmentFileItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;

    private boolean mDisplayOnly;
    private SerialBehaviorSubject<NoteAttachmentFile> mNoteAttachmentFile;
    private transient NoteAttachmentFileItemOnDeleteClick mNoteAttachmentFileItemOnDeleteClick;

    public NoteAttachmentFileItemSV() {
        this(false);
    }

    public NoteAttachmentFileItemSV(boolean displayOnly) {
        mDisplayOnly = displayOnly;
        mNoteAttachmentFile = new SerialBehaviorSubject<>(new NoteAttachmentFile());
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_note_attachment_file, container, false);
        ImageView imageView = rootLayout.findViewById(R.id.image);
        imageView.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        if (mDisplayOnly) {
            deleteButton.setVisibility(View.GONE);
        } else {
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(this);
        }
        mRxDisposer.add("createView_onNoteAttachmentFileChanged",
                mNoteAttachmentFile.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteAttachmentFile ->
                        {
                            if (noteAttachmentFile.fileName != null) {
                                imageView.setImageURI(Uri.fromFile(mFileHelper.getNoteAttachmentThumbnail(noteAttachmentFile.fileName)));
                            }
                        })
        );
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_delete) {
            if (mNoteAttachmentFileItemOnDeleteClick != null) {
                mNoteAttachmentFileItemOnDeleteClick.noteAttachmentFile_onDeleteClick(mNoteAttachmentFile.getValue());
            }
        } else if (id == R.id.image) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW, ImageViewPage.Args.withFile(
                    mFileHelper.getNoteAttachmentImage(mNoteAttachmentFile.getValue().fileName)
            ));
        }
    }

    public void setNoteAttachmentFile(NoteAttachmentFile noteAttachmentFile) {
        mNoteAttachmentFile.onNext(noteAttachmentFile);
    }

    public NoteAttachmentFile getNoteAttachmentFile() {
        return mNoteAttachmentFile.getValue();
    }

    public void setNoteAttachmentFileItemOnDeleteClick(NoteAttachmentFileItemOnDeleteClick noteAttachmentFileItemOnDeleteClick) {
        mNoteAttachmentFileItemOnDeleteClick = noteAttachmentFileItemOnDeleteClick;
    }

    public interface NoteAttachmentFileItemOnDeleteClick {
        void noteAttachmentFile_onDeleteClick(NoteAttachmentFile noteAttachmentFile);
    }
}
