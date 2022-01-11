package m.co.rh.id.a_medic_log.app.provider;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_medic_log.app.MainActivity;
import m.co.rh.id.a_medic_log.app.component.AppNotificationHandler;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.ui.page.SettingsPage;
import m.co.rh.id.a_medic_log.app.ui.page.SplashPage;
import m.co.rh.id.a_medic_log.base.provider.BaseProviderModule;
import m.co.rh.id.a_medic_log.base.provider.DatabaseProviderModule;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;
    private Navigator mNavigator;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());

        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider, context));
        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));

        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(INavigator.class, getNavigator(provider));
    }

    private Navigator getNavigator(Provider provider) {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
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
