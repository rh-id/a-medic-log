package m.co.rh.id.a_medic_log.app.provider;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.view.LayoutInflater;

import androidx.collection.ArrayMap;
import androidx.work.WorkManager;

import java.util.Map;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.a_medic_log.app.provider.component.FileCleanUpTask;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteAttachmentFileChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteTagChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.ui.page.DonationsPage;
import m.co.rh.id.a_medic_log.app.ui.page.HomePage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeListPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineReminderDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.NoteAttachmentDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.NoteDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.NoteTagDetailSVDialog;
import m.co.rh.id.a_medic_log.app.ui.page.NotesPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileSelectSVDialog;
import m.co.rh.id.a_medic_log.app.ui.page.ProfilesPage;
import m.co.rh.id.a_medic_log.app.ui.page.SettingsPage;
import m.co.rh.id.a_medic_log.app.ui.page.SplashPage;
import m.co.rh.id.a_medic_log.app.ui.page.common.CreateFileSVDialog;
import m.co.rh.id.a_medic_log.app.ui.page.common.ImageViewPage;
import m.co.rh.id.a_medic_log.base.provider.BaseProviderModule;
import m.co.rh.id.a_medic_log.base.provider.DatabaseProviderModule;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;
    @SuppressWarnings("rawtypes")
    private Navigator mNavigator;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());

        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(provider.getContext()));
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider));
        providerRegistry.registerAsync(ProfileChangeNotifier.class, ProfileChangeNotifier::new);
        providerRegistry.registerAsync(NoteChangeNotifier.class, NoteChangeNotifier::new);
        providerRegistry.registerAsync(NoteTagChangeNotifier.class, NoteTagChangeNotifier::new);
        providerRegistry.registerAsync(MedicineChangeNotifier.class, MedicineChangeNotifier::new);
        providerRegistry.registerAsync(MedicineReminderChangeNotifier.class, MedicineReminderChangeNotifier::new);
        providerRegistry.registerAsync(MedicineIntakeChangeNotifier.class, MedicineIntakeChangeNotifier::new);
        providerRegistry.registerAsync(NoteAttachmentFileChangeNotifier.class, NoteAttachmentFileChangeNotifier::new);
        providerRegistry.registerAsync(FileCleanUpTask.class, () -> new FileCleanUpTask(provider));
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider));
        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));

        providerRegistry.registerAsync(MedicineReminderEventHandler.class, () -> new MedicineReminderEventHandler(provider));
        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(NavExtDialogConfig.class, () -> new NavExtDialogConfig(provider.getContext()));
        providerRegistry.register(INavigator.class, () -> getNavigator(provider));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Navigator getNavigator(Provider provider) {
        Map<String, StatefulViewFactory<Activity, StatefulView<Activity>>> navMap = new ArrayMap<>();
        navMap.put(Routes.SPLASH_PAGE, (args, activity) -> new SplashPage(Routes.HOME_PAGE));
        navMap.put(Routes.HOME_PAGE, (args, activity) -> new HomePage());
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        navMap.put(Routes.DONATIONS_PAGE, (args, activity) -> new DonationsPage());
        navMap.put(Routes.PROFILES_PAGE, (args, activity) -> new ProfilesPage());
        navMap.put(Routes.PROFILE_SELECT_DIALOG, (args, activity) -> new ProfileSelectSVDialog());
        navMap.put(Routes.PROFILE_DETAIL_PAGE, (args, activity) -> new ProfileDetailPage());
        navMap.put(Routes.NOTES_PAGE, (args, activity) -> new NotesPage());
        navMap.put(Routes.NOTE_DETAIL_PAGE, (args, activity) -> new NoteDetailPage());
        navMap.put(Routes.NOTE_TAG_DETAIL_DIALOG, (args, activity) -> new NoteTagDetailSVDialog());
        navMap.put(Routes.NOTE_ATTACHMENT_DETAIL_PAGE, (args, activity) -> new NoteAttachmentDetailPage());
        navMap.put(Routes.MEDICINE_DETAIL_PAGE, (args, activity) -> new MedicineDetailPage());
        navMap.put(Routes.MEDICINE_REMINDER_DETAIL_PAGE, (args, activity) -> new MedicineReminderDetailPage());
        navMap.put(Routes.MEDICINE_INTAKES_PAGE, (args, activity) -> new MedicineIntakeListPage());
        navMap.put(Routes.MEDICINE_INTAKE_DETAIL_PAGE, (args, activity) -> new MedicineIntakeDetailPage());
        navMap.put(Routes.COMMON_CREATE_FILE_DIALOG, (args, activity) -> new CreateFileSVDialog());
        navMap.put(Routes.COMMON_IMAGEVIEW, (args, activity) -> new ImageViewPage());
        navMap.putAll(provider.get(NavExtDialogConfig.class).getNavMap());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder(Routes.SPLASH_PAGE, navMap);
        navBuilder.setRequiredComponent(provider);
        navBuilder.setMainHandler(provider.get(Handler.class));
        navBuilder.setLoadingView(LayoutInflater.from(provider.getContext())
                .inflate(R.layout.page_splash, null));
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mNavigator = navigator;
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Provider provider) {
        mApplication.unregisterActivityLifecycleCallbacks(mNavigator);
        mApplication.unregisterComponentCallbacks(mNavigator);
        mNavigator = null;
        mApplication = null;
    }
}
