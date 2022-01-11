package m.co.rh.id.a_medic_log.app.component;

import android.content.Context;
import android.os.Handler;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler {
    public static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";

    private static final String CHANNEL_ID_RSS_SYNC = "CHANNEL_ID_RSS_SYNC";
    private static final String GROUP_KEY_RSS_SYNC = "GROUP_KEY_RSS_SYNC";
    private static final int GROUP_SUMMARY_ID_RSS_SYNC = 0;

    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<Handler> mHandler;

    public AppNotificationHandler(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
    }
}
