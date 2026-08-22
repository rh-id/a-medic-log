package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateNoteCmd;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.note.detail.MedicineListSection;
import m.co.rh.id.a_medic_log.app.ui.component.note.detail.NoteAttachmentSection;
import m.co.rh.id.a_medic_log.app.ui.component.note.detail.NoteTagSection;
import m.co.rh.id.a_medic_log.app.util.SimpleTextWatcher;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class NoteDetailPage extends BaseDetailPage implements Toolbar.OnMenuItemClickListener, View.OnClickListener {

    private static final String TAG = NoteDetailPage.class.getName();
    @NavInject
    private AppBarSV mAppBarSv;

    private NoteState mNoteState;
    private SerialBehaviorSubject<Boolean> mNoteTagShow;
    private SerialBehaviorSubject<Boolean> mMedicineListShow;
    private SerialBehaviorSubject<Boolean> mAttachmentShow;

    private transient QueryNoteCmd mQueryNoteCmd;
    private transient NewNoteCmd mNewNoteCmd;
    private transient TextWatcher mEntryDateTimeTextWatcher;
    private transient TextWatcher mContentTextWatcher;
    private transient NoteTagSection mNoteTagSection;
    private transient MedicineListSection mMedicineListSection;
    private transient NoteAttachmentSection mNoteAttachmentSection;

    public NoteDetailPage() {
        mNoteTagShow = new SerialBehaviorSubject<>(false);
        mMedicineListShow = new SerialBehaviorSubject<>(false);
        mAttachmentShow = new SerialBehaviorSubject<>(false);
    }

    @Override
    protected void onProvideComponent(Provider provider) {
        mQueryNoteCmd = mSvProvider.get(QueryNoteCmd.class);
        boolean isUpdate = isUpdate();
        if (isUpdate) {
            mNewNoteCmd = mSvProvider.get(UpdateNoteCmd.class);
        } else {
            mNewNoteCmd = mSvProvider.get(NewNoteCmd.class);
        }
        if (mNoteState == null) {
            mNoteState = new NoteState();
            if (isUpdate) {
                mNoteState.setNoteId(getNoteId());
                mRxDisposer.add("NoteDetailPage.provideComponent_queryNoteInfo",
                        mQueryNoteCmd
                                .queryNoteInfo(mNoteState)
                                .subscribe((noteState, throwable) -> {
                                    if (throwable != null) {
                                        mLogger
                                                .e(TAG, throwable.getMessage(), throwable);
                                    }
                                })
                );
            } else {
                mNoteState.setNoteProfileId(getProfileId());
            }
        }
        if (mAppBarSv == null) {
            mAppBarSv = new AppBarSV(R.menu.page_note_detail);
        }
        if (isUpdate) {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_update_note));
        } else {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_add_note));
        }
        mAppBarSv.setMenuItemListener(this);
        initTextWatcher();
        Long routeNoteId = getNoteId();
        mNoteTagSection = new NoteTagSection(mNavigator, mSvProvider,
                mNoteState, mNoteTagShow, routeNoteId);
        mMedicineListSection = new MedicineListSection(mNavigator, mSvProvider,
                mNoteState, mMedicineListShow, routeNoteId, this);
        mNoteAttachmentSection = new NoteAttachmentSection(mNavigator, mSvProvider,
                mNoteState, mAttachmentShow, routeNoteId, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_note_detail, container, false);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSv.buildView(activity, appBarContainer));
        EditText entryDateTimeInput = rootLayout.findViewById(R.id.input_text_entry_date_time);
        entryDateTimeInput.setOnClickListener(this);
        entryDateTimeInput.addTextChangedListener(mEntryDateTimeTextWatcher);
        Button clearEntryDateTimeInput = rootLayout.findViewById(R.id.button_clear_entry_date_time);
        clearEntryDateTimeInput.setOnClickListener(this);
        EditText contentInput = rootLayout.findViewById(R.id.input_text_content);
        contentInput.addTextChangedListener(mContentTextWatcher);
        mNoteTagSection.bindViews(activity, rootLayout);
        mMedicineListSection.bindViews(activity, rootLayout);
        mNoteAttachmentSection.bindViews(activity, rootLayout);
        mRxDisposer.add("NoteDetailPage.createView_onNoteChanged",
                mNoteState.getNoteFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(note -> {
                            entryDateTimeInput.setText(mNoteState.getNoteEntryDateTimeDisplay());
                            contentInput.setText(mNoteState.getNoteContent());
                        }));
        mRxDisposer
                .add("NoteDetailPage.createView_onEntryDateTimeValidation",
                        mNewNoteCmd.getEntryDateTimeValid()
                                .observeOn(AndroidSchedulers.mainThread()).subscribe(error -> {
                            if (error != null && !error.isEmpty()) {
                                entryDateTimeInput.setError(error);
                            } else {
                                entryDateTimeInput.setError(null);
                            }
                        }));
        mRxDisposer
                .add("NoteDetailPage.createView_onContentValidation",
                        mNewNoteCmd.getContentValid()
                                .observeOn(AndroidSchedulers.mainThread()).subscribe(error -> {
                            if (error != null && !error.isEmpty()) {
                                contentInput.setError(error);
                            } else {
                                contentInput.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    protected void onPageDispose(Activity activity) {
        if (mAppBarSv != null) {
            mAppBarSv.dispose(activity);
            mAppBarSv = null;
        }
        mContentTextWatcher = null;
        if (mNoteTagSection != null) {
            mNoteTagSection.dispose();
            mNoteTagSection = null;
        }
        if (mMedicineListSection != null) {
            mMedicineListSection.dispose(activity);
            mMedicineListSection = null;
        }
        if (mNoteAttachmentSection != null) {
            mNoteAttachmentSection.dispose(activity);
            mNoteAttachmentSection = null;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mNewNoteCmd.valid(mNoteState)) {
                Context context = mSvProvider.getContext();
                String successMessage;
                if (isUpdate()) {
                    successMessage = context.getString(R.string.success_updating_note);
                } else {
                    successMessage = context.getString(R.string.success_adding_note);
                }
                RxUtils.executeAndLog(mRxDisposer, "NoteDetailPage.onMenuItemClick_newNoteCmd",
                        mNewNoteCmd.execute(mNoteState), mLogger, TAG, successMessage,
                        noteState -> mNavigator.pop(Result.withNote(noteState)));
            } else {
                String error = mNewNoteCmd.getValidationError();
                mLogger.i(TAG, error);
            }
        }
        return false;
    }

    private Long getProfileId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.getProfileId();
        }
        return null;
    }

    private Long getNoteId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.getNoteId();
        }
        return null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_entry_date_time) {
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.route_dateTimePickerDialog(),
                    navExtDialogConfig.args_dateTimePickerDialog(true, mNoteState.getNoteEntryDateTime()),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        NavExtDialogConfig navExtDialogConfig1 = provider.get(NavExtDialogConfig.class);
                        Date result = navExtDialogConfig1.result_dateTimePickerDialog(navRoute);
                        if (result != null) {
                            updateEntryDateTime(result);
                        }
                    });
        } else if (id == R.id.button_clear_entry_date_time) {
            updateEntryDateTime(null);
        }
    }

    private void updateEntryDateTime(Date date) {
        mNoteState.updateNoteEntryDateTime(date);
    }

    private void initTextWatcher() {
        if (mContentTextWatcher == null) {
            mContentTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    mNoteState.setNoteContent(editable.toString());
                    mNewNoteCmd.valid(mNoteState);
                }
            };
        }
        if (mEntryDateTimeTextWatcher == null) {
            mEntryDateTimeTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    mNewNoteCmd.valid(mNoteState);
                }
            };
        }
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    public static class Result implements Serializable {
        private static Result withNote(NoteState noteState) {
            Result result = new Result();
            result.mNoteState = noteState;
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private NoteState mNoteState;

        public NoteState getNoteState() {
            return mNoteState;
        }
    }

    public static class Args implements Serializable {
        public static Args withProfileId(long profileId) {
            Args args = new Args();
            args.mProfileId = profileId;
            return args;
        }

        public static Args forUpdate(long noteId) {
            Args args = new Args();
            args.mNoteId = noteId;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Long mProfileId;
        private Long mNoteId;

        private Args() {
        }

        public Long getProfileId() {
            return mProfileId;
        }

        public Long getNoteId() {
            return mNoteId;
        }

        public boolean isUpdate() {
            return mNoteId != null;
        }
    }
}
