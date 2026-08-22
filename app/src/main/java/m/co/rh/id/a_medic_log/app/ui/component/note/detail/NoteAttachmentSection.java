package m.co.rh.id.a_medic_log.app.ui.component.note.detail;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteAttachmentFileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.note.attachment.NoteAttachmentItemSV;
import m.co.rh.id.a_medic_log.app.ui.component.note.attachment.NoteAttachmentRecyclerViewAdapter;
import m.co.rh.id.a_medic_log.app.ui.page.NoteAttachmentDetailPage;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class NoteAttachmentSection implements View.OnClickListener,
        NoteAttachmentItemSV.NoteAttachmentItemOnEditClick, NoteAttachmentItemSV.NoteAttachmentItemOnDeleteClick {

    private static final String TAG = NoteAttachmentSection.class.getName();

    private final INavigator mNavigator;
    private final Provider mSvProvider;
    private final NoteState mNoteState;
    private final SerialBehaviorSubject<Boolean> mShowSubject;
    private final Long mRouteNoteId;
    private final ILogger mLogger;
    private final RxDisposer mRxDisposer;
    private final ExecutorService mExecutorService;
    private final QueryNoteCmd mQueryNoteCmd;
    private final DeleteNoteAttachmentCmd mDeleteNoteAttachmentCmd;
    private final NoteAttachmentFileChangeNotifier mNoteAttachmentFileChangeNotifier;
    private final StatefulView mParentStatefulView;

    private NoteAttachmentRecyclerViewAdapter mNoteAttachmentRecyclerViewAdapter;
    private TextView mAttachmentTitle;
    private RecyclerView mAttachmentRecyclerView;

    public NoteAttachmentSection(INavigator navigator, Provider svProvider,
                                 NoteState noteState, SerialBehaviorSubject<Boolean> showSubject,
                                 Long routeNoteId, StatefulView parentStatefulView) {
        mNavigator = navigator;
        mSvProvider = svProvider;
        mNoteState = noteState;
        mShowSubject = showSubject;
        mRouteNoteId = routeNoteId;
        mParentStatefulView = parentStatefulView;
        mLogger = svProvider.get(ILogger.class);
        mRxDisposer = svProvider.get(RxDisposer.class);
        mExecutorService = svProvider.get(ExecutorService.class);
        mQueryNoteCmd = svProvider.get(QueryNoteCmd.class);
        mDeleteNoteAttachmentCmd = svProvider.get(DeleteNoteAttachmentCmd.class);
        mNoteAttachmentFileChangeNotifier = svProvider.get(NoteAttachmentFileChangeNotifier.class);
        mNoteAttachmentRecyclerViewAdapter = new NoteAttachmentRecyclerViewAdapter(mNoteState, this, this, mNavigator, mParentStatefulView);
    }

    public void bindViews(Activity activity, ViewGroup rootLayout) {
        Button addAttachmentButton = rootLayout.findViewById(R.id.button_add_attachment);
        Button expandAttachment = rootLayout.findViewById(R.id.button_expand_attachment);
        View attachmentTextContainer = rootLayout.findViewById(R.id.container_attachment_text);
        mAttachmentTitle = rootLayout.findViewById(R.id.text_attachment_title);
        mAttachmentRecyclerView = rootLayout.findViewById(R.id.recyclerView_attachment);
        addAttachmentButton.setOnClickListener(this);
        expandAttachment.setOnClickListener(this);
        attachmentTextContainer.setOnClickListener(this);
        mAttachmentRecyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAttachmentRecyclerView.setAdapter(mNoteAttachmentRecyclerViewAdapter);
        mRxDisposer.add("NoteAttachmentSection.bindViews_onAttachmentChanged",
                mNoteState.getNoteAttachmentStatesFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteAttachmentStates -> {
                            mAttachmentTitle.setText(activity.getString(R.string.title_attachment, noteAttachmentStates.size()));
                            mNoteAttachmentRecyclerViewAdapter.notifyItemRefreshed();
                        }));
        mRxDisposer.add("NoteAttachmentSection.bindViews_onAttachmentShow",
                mShowSubject.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                mAttachmentRecyclerView.setVisibility(View.VISIBLE);
                            } else {
                                mAttachmentRecyclerView.setVisibility(View.GONE);
                            }
                            expandAttachment.setActivated(aBoolean);
                        }));
        mRxDisposer.add("NoteAttachmentSection.bindViews_onNoteAttachmentFileAdded",
                mNoteAttachmentFileChangeNotifier.getAddedNoteAttachmentFile()
                        .observeOn(Schedulers.from(mExecutorService))
                        .flatMapSingle(noteAttachmentFile -> {
                            if (isUpdate()) {
                                return mQueryNoteCmd.queryNoteAttachmentInfo(mNoteState);
                            }
                            return Single.just(mNoteState.getNoteAttachmentStates());
                        })
                        .subscribe(
                                noteAttachmentStates -> {},
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)
                        ));
        mRxDisposer.add("NoteAttachmentSection.bindViews_onNoteAttachmentFileDeleted",
                mNoteAttachmentFileChangeNotifier.getDeletedNoteAttachmentFile()
                        .observeOn(Schedulers.from(mExecutorService))
                        .flatMapSingle(noteAttachmentFile -> {
                            if (isUpdate()) {
                                return mQueryNoteCmd.queryNoteAttachmentInfo(mNoteState);
                            }
                            return Single.just(mNoteState.getNoteAttachmentStates());
                        })
                        .subscribe(
                                noteAttachmentStates -> {},
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)
                        ));
    }

    @Override
    public void noteAttachment_onEditClick(NoteAttachmentState noteAttachmentState) {
        NoteAttachmentDetailPage.Args args;
        if (isUpdate()) {
            args = NoteAttachmentDetailPage.Args.forUpdate(noteAttachmentState.clone());
        } else {
            args = NoteAttachmentDetailPage.Args.forEdit(noteAttachmentState.clone());
        }
        mNavigator.push(Routes.NOTE_ATTACHMENT_DETAIL_PAGE,
                args,
                (navigator, navRoute, activity, currentView) -> {
                    NoteAttachmentDetailPage.Result result = NoteAttachmentDetailPage.Result.of(navRoute);
                    if (result != null) {
                        updateNoteAttachmentState(result.getNoteAttachmentState());
                    }
                });
    }

    @Override
    public void noteAttachment_onDeleteClick(NoteAttachmentState noteAttachmentState) {
        if (isUpdate()) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_attachment);
            UiUtils.showConfirmDialog(mNavigator, mSvProvider, title, content,
                    () -> deleteNoteAttachment(noteAttachmentState));
        } else {
            mNoteAttachmentRecyclerViewAdapter.notifyItemDeleted(noteAttachmentState);
        }
    }

    private void updateNoteAttachmentState(NoteAttachmentState noteAttachmentState) {
        mNoteAttachmentRecyclerViewAdapter.notifyItemUpdated(noteAttachmentState);
    }

    private void deleteNoteAttachment(NoteAttachmentState noteAttachmentState) {
        mRxDisposer.add("NoteAttachmentSection.deleteNoteAttachment", mDeleteNoteAttachmentCmd
                .execute(noteAttachmentState)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((note, throwable) -> {
                    Context deleteContext = mSvProvider.getContext();
                    if (throwable != null) {
                        mLogger
                                .e(TAG,
                                        deleteContext.getString(
                                                R.string.error_deleting_note_attachment),
                                        throwable);
                    } else {
                        mLogger
                                .i(TAG,
                                        deleteContext.getString(
                                                R.string.success_deleting_note_attachment));
                        mNoteAttachmentRecyclerViewAdapter.notifyItemDeleted(noteAttachmentState);
                    }
                })
        );
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_attachment) {
            NoteAttachmentDetailPage.Args args;
            if (isUpdate()) {
                args = NoteAttachmentDetailPage.Args.save(mRouteNoteId);
            } else {
                args = NoteAttachmentDetailPage.Args.dontSave();
            }
            mNavigator.push(Routes.NOTE_ATTACHMENT_DETAIL_PAGE, args,
                    (navigator, navRoute, activity, currentView) -> {
                        NoteAttachmentDetailPage.Result result = NoteAttachmentDetailPage.Result.of(navRoute);
                        if (result != null) {
                            addNoteAttachment(result.getNoteAttachmentState());
                        }
                    });
        } else if (id == R.id.container_attachment_text || id == R.id.button_expand_attachment) {
            mShowSubject.onNext(!mShowSubject.getValue());
        }
    }

    private void addNoteAttachment(NoteAttachmentState noteAttachmentState) {
        mNoteState.addNoteAttachmentState(noteAttachmentState);
    }

    private boolean isUpdate() {
        return mRouteNoteId != null;
    }

    public void dispose(Activity activity) {
        if (mNoteAttachmentRecyclerViewAdapter != null) {
            mNoteAttachmentRecyclerViewAdapter.dispose(activity);
            mNoteAttachmentRecyclerViewAdapter = null;
        }
        mAttachmentTitle = null;
        mAttachmentRecyclerView = null;
    }
}
