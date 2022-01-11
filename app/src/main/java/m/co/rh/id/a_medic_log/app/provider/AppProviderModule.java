package m.co.rh.id.a_medic_log.app.provider;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.work.WorkManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.a_medic_log.app.provider.component.MedicineReminderEventHandler;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeListPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineReminderDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.NoteDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.NoteListPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileListPage;
import m.co.rh.id.a_medic_log.app.ui.page.ProfileSelectSVDialog;
import m.co.rh.id.a_medic_log.app.ui.page.SettingsPage;
import m.co.rh.id.a_medic_log.app.ui.page.SplashPage;
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
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());

        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(context, provider));
        providerRegistry.registerAsync(ProfileChangeNotifier.class, ProfileChangeNotifier::new);
        providerRegistry.registerAsync(NoteChangeNotifier.class, NoteChangeNotifier::new);
        providerRegistry.registerAsync(MedicineChangeNotifier.class, MedicineChangeNotifier::new);
        providerRegistry.registerAsync(MedicineReminderChangeNotifier.class, MedicineReminderChangeNotifier::new);
        providerRegistry.registerAsync(MedicineIntakeChangeNotifier.class, MedicineIntakeChangeNotifier::new);
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(context, provider));
        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));

        providerRegistry.registerAsync(MedicineReminderEventHandler.class, () -> new MedicineReminderEventHandler(provider));
        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(NavExtDialogConfig.class, new NavExtDialogConfig(context));
        providerRegistry.register(INavigator.class, getNavigator(provider));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Navigator getNavigator(Provider provider) {
        Map<String, StatefulViewFactory<Activity, StatefulView<Activity>>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        navMap.put(Routes.PROFILES_PAGE, (args, activity) -> new ProfileListPage());
        navMap.put(Routes.PROFILE_SELECT_DIALOG, (args, activity) -> new ProfileSelectSVDialog());
        navMap.put(Routes.PROFILE_DETAIL_PAGE, (args, activity) -> new ProfileDetailPage());
        navMap.put(Routes.NOTES_PAGE, (args, activity) -> new NoteListPage());
        navMap.put(Routes.NOTE_DETAIL_PAGE, (args, activity) -> new NoteDetailPage());
        navMap.put(Routes.MEDICINE_DETAIL_PAGE, (args, activity) -> new MedicineDetailPage());
        navMap.put(Routes.MEDICINE_REMINDER_DETAIL_PAGE, (args, activity) -> new MedicineReminderDetailPage());
        navMap.put(Routes.MEDICINE_INTAKES_PAGE, (args, activity) -> new MedicineIntakeListPage());
        navMap.put(Routes.MEDICINE_INTAKE_DETAIL_PAGE, (args, activity) -> new MedicineIntakeDetailPage());
        navMap.putAll(provider.get(NavExtDialogConfig.class).getNavMap());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder(Routes.HOME_PAGE, navMap);
        navBuilder.setSaveStateFile(new File(mApplication.getCacheDir(),
                "anavigator/Navigator.state"));
        navBuilder.setRequiredComponent(provider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mNavigator = navigator;
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Context context, Provider provider) {
        mApplication.unregisterActivityLifecycleCallbacks(mNavigator);
        mApplication.unregisterComponentCallbacks(mNavigator);
        mNavigator = null;
        mApplication = null;
    }
}
