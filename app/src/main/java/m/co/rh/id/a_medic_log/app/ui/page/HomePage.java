package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.ExportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.command.ExportSpreadsheetCmd;
import m.co.rh.id.a_medic_log.app.provider.command.ImportArchiveCmd;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.page.common.ProgressSVDialog;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, NavOnActivityResult<Activity>, DrawerLayout.DrawerListener, View.OnClickListener {
    private static final String TAG = HomePage.class.getName();

    private static final int REQUEST_CODE_EXPORT_ALL = 4001;
    private static final int REQUEST_CODE_EXPORT_PROFILES = 4002;
    private static final int REQUEST_CODE_IMPORT = 4003;
    private static final int REQUEST_CODE_EXPORT_SPREADSHEET = 4004;

    private static final String EXPORT_ZIP_MIME_TYPE = "application/zip";
    private static final String EXPORT_XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String EXPORT_ZIP_FILE_PREFIX = "a-medic-log-export-";
    private static final String EXPORT_TIMESTAMP_FORMAT = "yyyyMMdd-HHmmss";

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;

    // pending export/import request states
    private ArrayList<Long> mPendingExportProfileIds;
    private Profile mPendingSpreadsheetProfile;

    /**
     * Gate for the medicine-reminder navigation flow: {@code true} means a
     * reminder may push its pages immediately, {@code false} means a reminder
     * must be held (the non-dismissable progress dialog is showing, and a push
     * over it would strand the dialog because popProgressDialog() only pops
     * when the dialog is the top route). It is transient because the gate must
     * always start open: field initializers do not re-run when a-navigator
     * restores this page via Java deserialization after process death, and a
     * serialized closed gate would never reopen since the operation that
     * popped the dialog is gone by then.
     */
    private transient SerialBehaviorSubject<Boolean> mReminderNavigationGate;

    // component
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient MedicineDao mMedicineDao;
    private transient NoteDao mNoteDao;
    private transient Handler mHandler;
    private transient RxDisposer mRxDisposer;
    private transient AppNotificationHandler mAppNotificationHandler;
    private transient ILogger mLogger;
    private transient ExportArchiveCmd mExportArchiveCmd;
    private transient ImportArchiveCmd mImportArchiveCmd;
    private transient ExportSpreadsheetCmd mExportSpreadsheetCmd;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient View.OnClickListener mOnNavigationClicked;

    public HomePage() {
        mAppBarSV = new AppBarSV();
        mReminderNavigationGate = new SerialBehaviorSubject<>(true);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mMedicineDao = mSvProvider.get(MedicineDao.class);
        mNoteDao = mSvProvider.get(NoteDao.class);
        mHandler = mSvProvider.get(Handler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mAppNotificationHandler = mSvProvider.get(AppNotificationHandler.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExportArchiveCmd = mSvProvider.get(ExportArchiveCmd.class);
        mImportArchiveCmd = mSvProvider.get(ImportArchiveCmd.class);
        mExportSpreadsheetCmd = mSvProvider.get(ExportSpreadsheetCmd.class);
        if (mReminderNavigationGate == null) {
            // provideComponent also runs when a-navigator restores this page
            // from its process-death snapshot (deserialization skips the
            // constructor), where the gate must restart open because no
            // export/import can still be running
            mReminderNavigationGate = new SerialBehaviorSubject<>(true);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        View menuProfiles = view.findViewById(R.id.menu_profiles);
        menuProfiles.setOnClickListener(this);
        View menuNotes = view.findViewById(R.id.menu_notes);
        menuNotes.setOnClickListener(this);
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuDonations = view.findViewById(R.id.menu_donation);
        menuDonations.setOnClickListener(this);
        mDrawerLayout = view.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        if (mOnNavigationClicked == null) {
            mOnNavigationClicked = view1 -> {
                if (!mDrawerLayout.isOpen()) {
                    mDrawerLayout.open();
                }
            };
        }
        mAppBarSV.setTitle(activity.getString(R.string.title_home));
        mAppBarSV.setNavigationOnClick(mOnNavigationClicked);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        Button addProfileButton = view.findViewById(R.id.button_add_profile);
        addProfileButton.setOnClickListener(this);
        Button addNoteButton = view.findViewById(R.id.button_add_note);
        addNoteButton.setOnClickListener(this);
        Button exportAllButton = view.findViewById(R.id.button_export_all);
        exportAllButton.setOnClickListener(this);
        Button exportProfilesButton = view.findViewById(R.id.button_export_profiles);
        exportProfilesButton.setOnClickListener(this);
        Button importButton = view.findViewById(R.id.button_import);
        importButton.setOnClickListener(this);
        Button exportSpreadsheetButton = view.findViewById(R.id.button_export_spreadsheet);
        exportSpreadsheetButton.setOnClickListener(this);
        mRxDisposer.add("createView_onMedicineReminderNotification",
                mAppNotificationHandler.getMedicineReminderFlow()
                        // hold each reminder while the progress dialog shows,
                        // then release it once the gate reopens, so the reminder
                        // never pushes over the non-dismissable dialog.
                        // Boolean.TRUE::equals is null-safe (no NPE, null just
                        // fails the filter)
                        .flatMap(medicineReminder -> mReminderNavigationGate.getSubject()
                                .toFlowable(BackpressureStrategy.BUFFER)
                                .filter(Boolean.TRUE::equals)
                                .take(1)
                                .map(ignored -> medicineReminder))
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder -> {
                            Medicine medicine = mMedicineDao
                                    .findMedicineById(medicineReminder.medicineId);
                            Note note = mNoteDao
                                    .findNoteById(medicine.noteId);
                            mHandler
                                    .post(() -> {
                                        Long profileId = note.profileId;
                                        NavRoute navRoute = mNavigator.getCurrentRoute();
                                        if (!navRoute.getRouteName().equals(Routes.NOTES_PAGE)) {
                                            mNavigator.push(Routes.NOTES_PAGE,
                                                    NotesPage.Args.withProfileId(profileId));
                                        } else {
                                            NotesPage.Args args = NotesPage.Args.of(navRoute);
                                            if (args != null) {
                                                if (!profileId.equals(args.getProfileId())) {
                                                    mNavigator.push(Routes.NOTES_PAGE,
                                                            NotesPage.Args.withProfileId(profileId));
                                                }
                                            } else {
                                                mNavigator.push(Routes.NOTES_PAGE,
                                                        NotesPage.Args.withProfileId(profileId));
                                            }
                                        }
                                        mNavigator.push(Routes.NOTE_DETAIL_PAGE,
                                                NoteDetailPage.Args.forUpdate(medicine.noteId));
                                    });
                        }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mDrawerLayout = null;
        mOnNavigationClicked = null;
        mRxDisposer = null;
        mExportArchiveCmd = null;
        mImportArchiveCmd = null;
        mExportSpreadsheetCmd = null;
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (mDrawerLayout.isOpen()) {
            mDrawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                mSvProvider.get(ILogger.class).i(TAG,
                        activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        // Leave blank
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        // Leave blank
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.menu_profiles) {
            mNavigator.push(Routes.PROFILES_PAGE);
        } else if (id == R.id.menu_notes) {
            mNavigator.push(Routes.NOTES_PAGE);
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_donation) {
            mNavigator.push(Routes.DONATIONS_PAGE);
        } else if (id == R.id.button_add_profile) {
            mNavigator.push(Routes.PROFILE_DETAIL_PAGE);
        } else if (id == R.id.button_add_note) {
            NotesPage.addNoteWorkFlow(mNavigator, null);
        } else if (id == R.id.button_export_all) {
            startExportAllFlow();
        } else if (id == R.id.button_export_profiles) {
            startExportProfilesFlow();
        } else if (id == R.id.button_import) {
            startImportFlow();
        } else if (id == R.id.button_export_spreadsheet) {
            startExportSpreadsheetFlow();
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator,
                                 int requestCode, int resultCode, Intent data) {
        Uri uri = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            uri = data.getData();
        }
        if (uri == null) {
            // user cancelled the file picker or no file picked
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_EXPORT_ALL:
                exportZipToUri(R.string.title_export_all, R.string.export_progress_gathering,
                        mExportArchiveCmd.exportTo(null, uri));
                break;
            case REQUEST_CODE_EXPORT_PROFILES: {
                // capture and clear the pending ids so the stale reference does not outlive this flow
                ArrayList<Long> pendingExportProfileIds = mPendingExportProfileIds;
                mPendingExportProfileIds = null;
                if (pendingExportProfileIds == null) {
                    // null means "export all profiles", so exporting here after a process
                    // death during the picker would wrongly export every profile
                    mLogger.e(TAG, "Pending export profile selection is missing, "
                            + "likely due to process death while the file picker was open. "
                            + "Skipping profile export.");
                    return;
                }
                exportZipToUri(R.string.title_export_profiles, R.string.export_progress_gathering,
                        mExportArchiveCmd.exportTo(pendingExportProfileIds, uri));
                break;
            }
            case REQUEST_CODE_EXPORT_SPREADSHEET:
                exportSpreadsheetToUri(uri);
                break;
            case REQUEST_CODE_IMPORT:
                importFromUri(uri);
                break;
        }
    }

    private void startExportAllFlow() {
        startCreateDocumentAction(REQUEST_CODE_EXPORT_ALL, EXPORT_ZIP_MIME_TYPE,
                EXPORT_ZIP_FILE_PREFIX + newExportTimestamp() + ".zip");
    }

    private void startExportProfilesFlow() {
        mNavigator.push(Routes.PROFILE_SELECT_MULTI_PAGE,
                (navigator, navRoute, activity, currentView) -> {
                    ProfileSelectPage.Result result = ProfileSelectPage.Result.of(navRoute);
                    if (result == null) {
                        return;
                    }
                    ArrayList<Profile> selectedProfiles = result.getSelectedProfile();
                    ArrayList<Long> profileIds = new ArrayList<>();
                    for (Profile profile : selectedProfiles) {
                        profileIds.add(profile.id);
                    }
                    mPendingExportProfileIds = profileIds;
                    startCreateDocumentAction(REQUEST_CODE_EXPORT_PROFILES, EXPORT_ZIP_MIME_TYPE,
                            EXPORT_ZIP_FILE_PREFIX + newExportTimestamp() + ".zip");
                });
    }

    private void startImportFlow() {
        Activity activity = mNavigator.getActivity();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(EXPORT_ZIP_MIME_TYPE);
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    private void startExportSpreadsheetFlow() {
        mNavigator.push(Routes.PROFILE_SELECT_PAGE,
                (navigator, navRoute, activity, currentView) -> {
                    ProfileSelectPage.Result result = ProfileSelectPage.Result.of(navRoute);
                    if (result == null) {
                        return;
                    }
                    Profile profile = result.getSelectedProfile().get(0);
                    mPendingSpreadsheetProfile = profile;
                    String fileName = profile.name + "-export-" + newExportTimestamp()
                            + (isSpreadsheetSupported() ? ".xlsx" : ".zip");
                    startCreateDocumentAction(REQUEST_CODE_EXPORT_SPREADSHEET,
                            isSpreadsheetSupported() ? EXPORT_XLSX_MIME_TYPE : EXPORT_ZIP_MIME_TYPE,
                            fileName);
                });
    }

    private void startCreateDocumentAction(int requestCode, String mimeType, String fileName) {
        Activity activity = mNavigator.getActivity();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * POI based spreadsheet export only supports API 26+,
     * below that a CSV archive is exported instead
     */
    private static boolean isSpreadsheetSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    private static String newExportTimestamp() {
        return new SimpleDateFormat(EXPORT_TIMESTAMP_FORMAT, Locale.getDefault())
                .format(new Date());
    }

    private void exportZipToUri(int titleRes, int messageRes, Single<Uri> exportSingle) {
        Activity activity = mNavigator.getActivity();
        // close the gate before pushing so no reminder slips in during the
        // push window; anything held is released when the dialog is popped
        mReminderNavigationGate.onNext(false);
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, ProgressSVDialog.Args.newArgs(
                activity.getString(titleRes),
                activity.getString(messageRes)));
        Context context = mSvProvider.getContext();
        mRxDisposer.add("HomePage_exportZip", exportSingle
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((destUri, throwable) -> {
                    popProgressDialog();
                    if (throwable != null) {
                        RxUtils.logError(mLogger, TAG,
                                context.getString(R.string.error_exporting_data), throwable);
                    } else {
                        mLogger.i(TAG, context.getString(R.string.success_exporting_data));
                    }
                }));
    }

    private void exportSpreadsheetToUri(Uri destUri) {
        Profile profile = mPendingSpreadsheetProfile;
        // clear the pending profile so the stale reference does not outlive this flow
        mPendingSpreadsheetProfile = null;
        if (profile == null || profile.id == null) {
            return;
        }
        exportZipToUri(R.string.title_export_spreadsheet,
                R.string.export_spreadsheet_progress_writing,
                mExportSpreadsheetCmd.exportTo(profile.id, destUri));
    }

    private void importFromUri(Uri zipUri) {
        Activity activity = mNavigator.getActivity();
        // close the gate before pushing so no reminder slips in during the
        // push window; anything held is released when the dialog is popped
        mReminderNavigationGate.onNext(false);
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, ProgressSVDialog.Args.newArgs(
                activity.getString(R.string.title_import),
                activity.getString(R.string.import_progress_reading)));
        Context context = mSvProvider.getContext();
        mRxDisposer.add("HomePage_importPeek", mImportArchiveCmd.peek(zipUri)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((fileProfiles, throwable) -> {
                    popProgressDialog();
                    if (throwable != null) {
                        RxUtils.logError(mLogger, TAG,
                                context.getString(R.string.error_importing_data), throwable);
                        return;
                    }
                    if (fileProfiles.isEmpty()) {
                        mLogger.i(TAG, context.getString(R.string.error_no_profile_in_import_file));
                        return;
                    }
                    pushImportProfileSelect(zipUri, fileProfiles);
                }));
    }

    private void pushImportProfileSelect(Uri zipUri,
                                         List<ImportArchiveCmd.FileProfile> fileProfiles) {
        mNavigator.push(Routes.IMPORT_PROFILE_SELECT_PAGE,
                ImportProfileSelectPage.Args.withFileProfiles(fileProfiles),
                (navigator, navRoute, activity, currentView) -> {
                    ImportProfileSelectPage.Result result =
                            ImportProfileSelectPage.Result.of(navRoute);
                    if (result == null) {
                        return;
                    }
                    confirmImport(zipUri, result);
                });
    }

    private void confirmImport(Uri zipUri, ImportProfileSelectPage.Result result) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm);
        String content = context.getString(R.string.confirm_import_profiles,
                result.getSelectedFileProfileIds().size());
        UiUtils.showConfirmDialog(mNavigator, mSvProvider, title, content,
                () -> importProfiles(zipUri, result));
    }

    private void importProfiles(Uri zipUri, ImportProfileSelectPage.Result result) {
        Activity activity = mNavigator.getActivity();
        // close the gate before pushing so no reminder slips in during the
        // push window; anything held is released when the dialog is popped
        mReminderNavigationGate.onNext(false);
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, ProgressSVDialog.Args.newArgs(
                activity.getString(R.string.title_import),
                activity.getString(R.string.import_progress_importing)));
        Context context = mSvProvider.getContext();
        mRxDisposer.add("HomePage_importProfiles", mImportArchiveCmd
                .importProfiles(zipUri, result.getSelectedFileProfileIds())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> popProgressDialog())
                .subscribe((importResult, throwable) -> {
                    if (throwable != null) {
                        RxUtils.logError(mLogger, TAG,
                                context.getString(R.string.error_importing_data), throwable);
                        return;
                    }
                    mLogger.i(TAG, context.getString(R.string.success_importing_data,
                            importResult.profileCount, importResult.noteCount));
                }));
    }

    /**
     * Pops the progress dialog only when it is still the current route,
     * so a late completion never pops an unrelated route (e.g. after the
     * operation was abandoned by exiting the app).
     * <p>
     * Afterwards the medicine-reminder navigation gate is always reopened,
     * releasing any reminders held while the dialog was showing.
     */
    private void popProgressDialog() {
        NavRoute currentRoute = mNavigator.getCurrentRoute();
        if (currentRoute != null
                && Routes.COMMON_PROGRESS_DIALOG.equals(currentRoute.getRouteName())) {
            mNavigator.pop();
        }
        // the operation is done: let held medicine-reminder navigation proceed
        mReminderNavigationGate.onNext(true);
    }

}
