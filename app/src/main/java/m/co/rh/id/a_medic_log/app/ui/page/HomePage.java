package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements Externalizable, RequireComponent<Provider>, NavOnBackPressed<Activity>, DrawerLayout.DrawerListener, View.OnClickListener {
    private static final String TAG = HomePage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;

    // component
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient MedicineDao mMedicineDao;
    private transient NoteDao mNoteDao;
    private transient Handler mHandler;
    private transient RxDisposer mRxDisposer;
    private transient AppNotificationHandler mAppNotificationHandler;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient View.OnClickListener mOnNavigationClicked;

    public HomePage() {
        mAppBarSV = new AppBarSV();
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
        mRxDisposer.add("createView_onMedicineReminderNotification",
                mAppNotificationHandler.getMedicineReminderFlow()
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
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        objectOutput.writeObject(mAppBarSV);
        objectOutput.writeBoolean(mIsDrawerOpen);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws ClassNotFoundException, IOException {
        super.readExternal(objectInput);
        mAppBarSV = (AppBarSV) objectInput.readObject();
        mIsDrawerOpen = objectInput.readBoolean();
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
        }
    }
}
