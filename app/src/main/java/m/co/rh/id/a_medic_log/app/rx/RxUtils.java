package m.co.rh.id.a_medic_log.app.rx;

import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.alogger.ILogger;

/**
 * Helper to execute command and handle its success or error result.
 */
public class RxUtils {

    /**
     * Log the cause of the throwable if any, otherwise the throwable itself.
     */
    public static void logError(ILogger logger, String tag, Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (cause == null) {
            cause = throwable;
        }
        logger.e(tag, cause.getMessage(), cause);
    }

    /**
     * Log custom message with the cause of the throwable if any, otherwise the throwable itself.
     */
    public static void logError(ILogger logger, String tag, String message, Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (cause == null) {
            cause = throwable;
        }
        logger.e(tag, message, cause);
    }

    public static <T> void executeAndLog(RxDisposer disposer, String key, Single<T> single,
                                         ILogger logger, String tag, String successMessage) {
        executeAndLog(disposer, key, single, logger, tag, successMessage, null);
    }

    /**
     * Execute command on main thread, log its result and run onSuccess on success.
     */
    public static <T> void executeAndLog(RxDisposer disposer, String key, Single<T> single,
                                         ILogger logger, String tag, String successMessage,
                                         Consumer<T> onSuccess) {
        disposer.add(key, single
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result, throwable) -> {
                    if (throwable != null) {
                        logError(logger, tag, throwable);
                        return;
                    }
                    logger.i(tag, successMessage);
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                }));
    }

    private RxUtils() {
    }
}
