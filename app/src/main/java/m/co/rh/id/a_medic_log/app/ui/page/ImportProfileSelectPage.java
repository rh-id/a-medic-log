package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.ImportArchiveCmd;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Page to select which profiles from an import file will be imported.
 * The profile list comes from the import file itself (see {@link ImportArchiveCmd#peek(android.net.Uri)}),
 * it is not database bound, selected file profile ids are returned as the result.
 */
public class ImportProfileSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = ImportProfileSelectPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient ILogger mLogger;
    private transient LinearLayout mContainerCheckBoxes;

    @NavInject
    private AppBarSV mAppBarSV;

    private final ArrayList<ImportArchiveCmd.FileProfile> mFileProfiles;

    public ImportProfileSelectPage() {
        mFileProfiles = new ArrayList<>();
        mAppBarSV = new AppBarSV();
    }

    public ImportProfileSelectPage(Args args) {
        if (args == null || args.getFileProfiles() == null) {
            mFileProfiles = new ArrayList<>();
        } else {
            mFileProfiles = args.getFileProfiles();
        }
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_profile_select, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_select_profile_to_import));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(createCheckBoxList(activity));
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        return rootLayout;
    }

    private View createCheckBoxList(Activity activity) {
        ScrollView scrollView = new ScrollView(activity);
        mContainerCheckBoxes = new LinearLayout(activity);
        mContainerCheckBoxes.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mContainerCheckBoxes, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Resources resources = activity.getResources();
        int margin = resources.getDimensionPixelSize(R.dimen.text_margin);
        // indent the count summary to align under the checkbox text
        int countLeftMargin = margin * 2;
        int size = mFileProfiles.size();
        for (int i = 0; i < size; i++) {
            ImportArchiveCmd.FileProfile fileProfile = mFileProfiles.get(i);
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.VERTICAL);
            CheckBox checkBox = new CheckBox(activity);
            checkBox.setText(getProfileDisplayName(fileProfile));
            checkBox.setChecked(true);
            checkBox.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.addView(checkBox, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView countTextView = new TextView(activity);
            countTextView.setText(buildProfileCountSummary(activity, fileProfile));
            countTextView.setTextColor(ContextCompat.getColor(activity, R.color.daynight_gray_700_white));
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            countParams.setMargins(countLeftMargin, 0, margin, 0);
            rowLayout.addView(countTextView, countParams);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(margin, margin, margin, 0);
            mContainerCheckBoxes.addView(rowLayout, rowParams);
        }
        return scrollView;
    }

    /**
     * @return a single line summary of the file profile contents, e.g.
     * "5 notes · 2 medicines · 3 reminders · 8 attachments" (singular aware)
     */
    private static String buildProfileCountSummary(Activity activity,
                                                   ImportArchiveCmd.FileProfile fileProfile) {
        Resources resources = activity.getResources();
        String notes = resources.getQuantityString(R.plurals.profile_count_notes,
                fileProfile.noteCount, fileProfile.noteCount);
        String medicines = resources.getQuantityString(R.plurals.profile_count_medicines,
                fileProfile.medicineCount, fileProfile.medicineCount);
        String reminders = resources.getQuantityString(R.plurals.profile_count_reminders,
                fileProfile.reminderCount, fileProfile.reminderCount);
        String attachments = resources.getQuantityString(R.plurals.profile_count_attachments,
                fileProfile.attachmentCount, fileProfile.attachmentCount);
        return notes + " \u00B7 " + medicines + " \u00B7 " + reminders + " \u00B7 " + attachments;
    }

    private String getProfileDisplayName(ImportArchiveCmd.FileProfile fileProfile) {
        if (fileProfile.name == null || fileProfile.name.isEmpty()) {
            return Long.toString(fileProfile.fileProfileId);
        }
        return fileProfile.name;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_cancel) {
            mNavigator.pop();
        } else if (viewId == R.id.button_ok) {
            ArrayList<Long> selectedIds = new ArrayList<>();
            ArrayList<String> selectedNames = new ArrayList<>();
            int size = mContainerCheckBoxes.getChildCount();
            for (int i = 0; i < size; i++) {
                ViewGroup rowLayout = (ViewGroup) mContainerCheckBoxes.getChildAt(i);
                CheckBox checkBox = (CheckBox) rowLayout.getChildAt(0);
                if (checkBox.isChecked()) {
                    ImportArchiveCmd.FileProfile fileProfile = mFileProfiles.get(i);
                    selectedIds.add(fileProfile.fileProfileId);
                    selectedNames.add(getProfileDisplayName(fileProfile));
                }
            }
            if (!selectedIds.isEmpty()) {
                mNavigator.pop(Result.selectedFileProfiles(selectedIds, selectedNames));
            } else {
                mLogger.i(TAG, view.getContext().getString(R.string.error_please_select_profile));
            }
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
    }

    /**
     * Args of this page, contains the profile list found inside an import file
     */
    public static class Args implements Serializable {
        public static Args withFileProfiles(List<ImportArchiveCmd.FileProfile> fileProfiles) {
            Args args = new Args();
            if (fileProfiles == null) {
                args.mFileProfiles = new ArrayList<>();
            } else {
                args.mFileProfiles = new ArrayList<>(fileProfiles);
            }
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

        private ArrayList<ImportArchiveCmd.FileProfile> mFileProfiles;

        private Args() {
        }

        public ArrayList<ImportArchiveCmd.FileProfile> getFileProfiles() {
            return mFileProfiles;
        }
    }

    /**
     * Result of this page, contains the selected file profile ids and names
     */
    public static class Result implements Serializable {
        public static Result selectedFileProfiles(ArrayList<Long> selectedIds,
                                                  ArrayList<String> selectedNames) {
            Result result = new Result();
            result.mSelectedFileProfileIds = selectedIds;
            result.mSelectedFileProfileNames = selectedNames;
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

        private ArrayList<Long> mSelectedFileProfileIds;
        private ArrayList<String> mSelectedFileProfileNames;

        private Result() {
        }

        public ArrayList<Long> getSelectedFileProfileIds() {
            return mSelectedFileProfileIds;
        }

        public ArrayList<String> getSelectedFileProfileNames() {
            return mSelectedFileProfileNames;
        }
    }
}
