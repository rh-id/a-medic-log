package m.co.rh.id.a_medic_log.app.ui.component.note.attachment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Constants;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class NoteAttachmentItemSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = NoteAttachmentItemSV.class.getName();

    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;
    private SerialBehaviorSubject<NoteAttachmentState> mNoteAttachmentStateSubject;

    private transient NoteAttachmentItemOnEditClick mNoteAttachmentItemOnEditClick;
    private transient NoteAttachmentItemOnDeleteClick mNoteAttachmentItemOnDeleteClick;
    private transient NoteAttachmentFileRecyclerViewAdapter mNoteAttachmentFileRecyclerViewAdapter;

    public NoteAttachmentItemSV() {
        mNoteAttachmentStateSubject = new SerialBehaviorSubject<>(new NoteAttachmentState());
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_note_attachment, container, false);
        rootLayout.setOnClickListener(this);
        Button buttonShare = rootLayout.findViewById(R.id.button_share);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        buttonShare.setOnClickListener(this);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        RecyclerView noteAttachmentFileRecyclerView = rootLayout.findViewById(R.id.recyclerView_note_attachment_file);
        mRxDisposer.add("createView_onNoteAttachmentChanged",
                mNoteAttachmentStateSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteAttachmentState -> {
                            int size = noteAttachmentState.getNoteAttachmentFiles().size();
                            nameText.setText(noteAttachmentState.getName() + " (" + size + ") ");
                            if (mNoteAttachmentFileRecyclerViewAdapter != null) {
                                noteAttachmentFileRecyclerView.setAdapter(null);
                                mNoteAttachmentFileRecyclerViewAdapter.dispose(activity);
                            }
                            if (!noteAttachmentState.getNoteAttachmentFiles().isEmpty()) {
                                mNoteAttachmentFileRecyclerViewAdapter =
                                        new NoteAttachmentFileRecyclerViewAdapter(getNoteAttachmentState(), null, true,
                                                mNavigator, this);
                                noteAttachmentFileRecyclerView.setAdapter(mNoteAttachmentFileRecyclerViewAdapter);
                                noteAttachmentFileRecyclerView.setVisibility(View.VISIBLE);
                            } else {
                                noteAttachmentFileRecyclerView.setVisibility(View.GONE);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNoteAttachmentItemOnEditClick = null;
        mNoteAttachmentItemOnDeleteClick = null;
        if (mNoteAttachmentFileRecyclerViewAdapter != null) {
            mNoteAttachmentFileRecyclerViewAdapter.dispose(activity);
            mNoteAttachmentFileRecyclerViewAdapter = null;
        }
    }

    public void setNoteAttachmentState(NoteAttachmentState noteAttachmentState) {
        mNoteAttachmentStateSubject.onNext(noteAttachmentState);
    }

    public NoteAttachmentState getNoteAttachmentState() {
        return mNoteAttachmentStateSubject.getValue();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_edit) {
            if (mNoteAttachmentItemOnEditClick != null) {
                mNoteAttachmentItemOnEditClick.noteAttachment_onEditClick(mNoteAttachmentStateSubject.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mNoteAttachmentItemOnDeleteClick != null) {
                mNoteAttachmentItemOnDeleteClick.noteAttachment_onDeleteClick(mNoteAttachmentStateSubject.getValue());
            }
        } else if (id == R.id.button_share) {
            Activity activity = mNavigator.getActivity();
            NoteAttachmentState noteAttachmentState = mNoteAttachmentStateSubject.getValue();
            String text = noteAttachmentState.getName();
            ArrayList<NoteAttachmentFile> attachmentFiles = noteAttachmentState.getNoteAttachmentFiles();
            ArrayList<Uri> imageUris = new ArrayList<>();
            if (!attachmentFiles.isEmpty()) {
                for (NoteAttachmentFile noteAttachmentFile : attachmentFiles) {
                    Uri fileUri =
                            FileProvider.getUriForFile(
                                    activity,
                                    Constants.FILE_PROVIDER_AUTHORITY,
                                    mFileHelper.getNoteAttachmentImage(noteAttachmentFile.fileName));
                    imageUris.add(fileUri);
                }
            }
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            sendIntent.putExtra(Intent.EXTRA_TEXT, text);
            sendIntent.setType("image/*");
            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
            Intent shareIntent = Intent.createChooser(sendIntent, null);
            activity.startActivity(shareIntent);
        }
    }

    public void setNoteAttachmentItemOnEditClick(NoteAttachmentItemOnEditClick noteAttachmentItemOnEditClick) {
        mNoteAttachmentItemOnEditClick = noteAttachmentItemOnEditClick;
    }

    public void setNoteAttachmentItemOnDeleteClick(NoteAttachmentItemOnDeleteClick noteAttachmentItemOnDeleteClick) {
        mNoteAttachmentItemOnDeleteClick = noteAttachmentItemOnDeleteClick;
    }

    public interface NoteAttachmentItemOnEditClick {
        void noteAttachment_onEditClick(NoteAttachmentState noteAttachmentState);
    }

    public interface NoteAttachmentItemOnDeleteClick {
        void noteAttachment_onDeleteClick(NoteAttachmentState noteAttachmentState);
    }
}
