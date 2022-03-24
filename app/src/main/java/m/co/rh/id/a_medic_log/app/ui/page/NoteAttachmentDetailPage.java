package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteAttachmentFileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteAttachmentFileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.note.attachment.NoteAttachmentFileItemSV;
import m.co.rh.id.a_medic_log.app.ui.component.note.attachment.NoteAttachmentFileRecyclerViewAdapter;
import m.co.rh.id.a_medic_log.app.ui.page.common.CreateFileSVDialog;
import m.co.rh.id.a_medic_log.base.entity.NoteAttachmentFile;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.a_medic_log.base.state.NoteAttachmentState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class NoteAttachmentDetailPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener, View.OnClickListener, NoteAttachmentFileItemSV.NoteAttachmentFileItemOnDeleteClick {

    private static final String TAG = NoteAttachmentDetailPage.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;
    private transient ILogger mLogger;
    private transient NewNoteAttachmentCmd mNewNoteAttachmentCmd;
    private transient NewNoteAttachmentFileCmd mNewNoteAttachmentFileCmd;
    private transient DeleteNoteAttachmentFileCmd mDeleteNoteAttachmentFileCmd;

    @NavInject
    private AppBarSV mAppBarSV;
    private NoteAttachmentState mNoteAttachmentState;
    private transient TextWatcher mNameTextWatcher;
    private transient NoteAttachmentFileRecyclerViewAdapter mNoteAttachmentFileRecyclerViewAdapter;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNewNoteAttachmentFileCmd = mSvProvider.get(NewNoteAttachmentFileCmd.class);
        mDeleteNoteAttachmentFileCmd = mSvProvider.get(DeleteNoteAttachmentFileCmd.class);
        boolean isUpdate = isUpdate();
        if (isUpdate) {
            mNewNoteAttachmentCmd = mSvProvider.get(UpdateNoteAttachmentCmd.class);
        } else {
            mNewNoteAttachmentCmd = mSvProvider.get(NewNoteAttachmentCmd.class);
        }
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(R.menu.page_note_attachment_detail);
        }
        if (mNoteAttachmentState == null) {
            if (isUpdate) {
                mNoteAttachmentState = getNoteAttachmentState();
            } else {
                mNoteAttachmentState = new NoteAttachmentState();
                if (shouldSave()) {
                    mNoteAttachmentState.setNoteId(getNoteId());
                }
            }
        }
        mNameTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String name = editable.toString();
                mNoteAttachmentState.setName(name);
                mNewNoteAttachmentCmd.valid(mNoteAttachmentState);
            }
        };
        mNoteAttachmentFileRecyclerViewAdapter = new NoteAttachmentFileRecyclerViewAdapter(mNoteAttachmentState, this, mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_note_attachment_detail, container, false);
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_note_attachment));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_note_attachment));
        }
        mAppBarSV.setMenuItemListener(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        EditText inputTextName = rootLayout.findViewById(R.id.input_text_name);
        inputTextName.setText(mNoteAttachmentState.getName());
        inputTextName.addTextChangedListener(mNameTextWatcher);
        Button addNoteAttachmentFileButton = rootLayout.findViewById(R.id.button_add_note_attachment_file);
        addNoteAttachmentFileButton.setOnClickListener(this);
        RecyclerView noteAttachmentFileRecyclerView = rootLayout.findViewById(R.id.recyclerView_note_attachment_file);
        noteAttachmentFileRecyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        noteAttachmentFileRecyclerView.setAdapter(mNoteAttachmentFileRecyclerViewAdapter);
        mRxDisposer.add("createView_onNameValid",
                mNewNoteAttachmentCmd.getNameValid().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputTextName.setError(s);
                            } else {
                                inputTextName.setError(null);
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
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            if (shouldSave()) {
                if (mNewNoteAttachmentCmd.valid(mNoteAttachmentState)) {
                    mRxDisposer.add("onMenuItemClick_save",
                            mNewNoteAttachmentCmd.execute(mNoteAttachmentState)
                                    .subscribe((noteAttachmentState, throwable) -> {
                                        if (throwable != null) {
                                            mLogger.e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            Context context = mSvProvider.getContext();
                                            if (isUpdate()) {
                                                mLogger.i(TAG, context.getString(R.string.success_updating_note_attachment));
                                            } else {
                                                mLogger.i(TAG, context.getString(R.string.success_adding_note_attachment));
                                            }
                                            mNavigator.pop(Result.with(noteAttachmentState));
                                        }
                                    }));
                } else {
                    String error = mNewNoteAttachmentCmd.getValidationError();
                    mLogger.i(TAG, error);
                    mNavigator.pop();
                }
            } else {
                mNavigator.pop(Result.with(mNoteAttachmentState));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_note_attachment_file) {
            mNavigator.push(Routes.COMMON_CREATE_FILE_DIALOG,
                    (navigator, navRoute, activity, currentView) -> {
                        CreateFileSVDialog.Result result = CreateFileSVDialog.Result.of(navRoute);
                        if (result != null) {
                            addNoteAttachmentFile(result.getFile());
                        }
                    });
        }
    }

    private void addNoteAttachmentFile(File file) {
        mRxDisposer.add("addNoteAttachmentFile",
                Single.fromFuture(mExecutorService.submit(() -> {
                    String fileName = file.getName();
                    Future<File> imageFile = mExecutorService.submit(() -> mFileHelper.createNoteAttachmentImage(Uri.fromFile(file), fileName));
                    Future<File> thumbnailFile = mExecutorService.submit(() -> mFileHelper.createNoteAttachmentThumbnail(Uri.fromFile(file), fileName));
                    NoteAttachmentFile noteAttachmentFile = new NoteAttachmentFile();
                    noteAttachmentFile.fileName = fileName;
                    thumbnailFile.get(); // wait till finish
                    imageFile.get(); // wait till finish
                    return noteAttachmentFile;
                })).observeOn(AndroidSchedulers.mainThread())
                        .subscribe((noteAttachmentFile, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) {
                                    cause = throwable;
                                }
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                if (isUpdate()) {
                                    noteAttachmentFile.attachmentId = mNoteAttachmentState.getId();
                                    mRxDisposer.add("addNoteAttachmentFile_newNoteAttachmentFile",
                                            mNewNoteAttachmentFileCmd.execute(noteAttachmentFile)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((savedNoteAttachmentFile, newNoteAttachmentFileThrowable) -> {
                                                        if (newNoteAttachmentFileThrowable != null) {
                                                            Throwable cause = newNoteAttachmentFileThrowable.getCause();
                                                            if (cause == null) {
                                                                cause = newNoteAttachmentFileThrowable;
                                                            }
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mNoteAttachmentFileRecyclerViewAdapter.notifyItemAdded(savedNoteAttachmentFile);
                                                        }
                                                    }));

                                } else {
                                    mNoteAttachmentFileRecyclerViewAdapter.notifyItemAdded(noteAttachmentFile);
                                }
                            }
                        }));
    }

    @Override
    public void noteAttachmentFile_onDeleteClick(NoteAttachmentFile noteAttachmentFile) {
        if (isUpdate()) {
            mRxDisposer.add("noteAttachmentFile_onDeleteClick_delete",
                    mDeleteNoteAttachmentFileCmd.execute(noteAttachmentFile)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedNoteAttachmentFile, throwable) -> {
                                Context context = mSvProvider.getContext();
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, context.getString(R.string.error_deleting_note_attachment_file), cause);
                                }
                            }));

        }
        mNoteAttachmentFileRecyclerViewAdapter.notifyItemDeleted(noteAttachmentFile);
    }

    private Boolean shouldSave() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.shouldSave;
        }
        return null;
    }

    private Long getNoteId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.noteId;
        }
        return null;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    private NoteAttachmentState getNoteAttachmentState() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.noteAttachmentState;
        }
        return null;
    }

    public static class Result implements Serializable {
        static Result with(NoteAttachmentState noteAttachmentState) {
            Result result = new Result();
            result.noteAttachmentState = noteAttachmentState;
            return result;
        }

        private NoteAttachmentState noteAttachmentState;

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable result = navRoute.getRouteResult();
                if (result instanceof Result) {
                    return (Result) result;
                }
            }
            return null;
        }

        public NoteAttachmentState getNoteAttachmentState() {
            return noteAttachmentState;
        }
    }

    public static class Args implements Serializable {
        public static Args dontSave() {
            Args args = new Args();
            args.shouldSave = false;
            return args;
        }

        public static Args save(long noteId) {
            Args args = new Args();
            args.shouldSave = true;
            args.noteId = noteId;
            return args;
        }

        public static Args forUpdate(NoteAttachmentState noteAttachmentState) {
            Args args = new Args();
            args.shouldSave = true;
            args.noteAttachmentState = noteAttachmentState;
            return args;
        }

        public static Args forEdit(NoteAttachmentState noteAttachmentState) {
            Args args = new Args();
            args.shouldSave = false;
            args.noteAttachmentState = noteAttachmentState;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable args = navRoute.getRouteArgs();
                if (args instanceof Args) {
                    return (Args) args;
                }
            }
            return null;
        }

        private Boolean shouldSave;
        private Long noteId;
        private NoteAttachmentState noteAttachmentState;

        private boolean isUpdate() {
            return noteAttachmentState != null;
        }
    }
}
