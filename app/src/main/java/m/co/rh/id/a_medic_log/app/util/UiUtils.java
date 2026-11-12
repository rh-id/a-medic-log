package m.co.rh.id.a_medic_log.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.File;

import m.co.rh.id.a_medic_log.app.constants.Constants;
import m.co.rh.id.a_medic_log.base.model.SerializableRunnable;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavPopCallback;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class UiUtils {

    /**
     * The pop callback is serialized with the route stack, so {@code onConfirm}
     * must only capture serializable state (a plain {@link Runnable} lambda
     * never serializes — the snapshot save would silently fail while the
     * dialog is on the stack).
     */
    public static void showConfirmDialog(INavigator navigator,
                                         String title, String content, SerializableRunnable onConfirm) {
        showConfirmDialog(navigator, title, content,
                (navigator1, navRoute, activity, currentView) -> {
                    Boolean result = readConfirmResult(navigator1, navRoute);
                    if (result != null && result) {
                        onConfirm.run();
                    }
                });
    }

    /**
     * Same confirm dialog as
     * {@link #showConfirmDialog(INavigator, String, String, SerializableRunnable)}
     * but with the raw pop callback (invoked on confirm AND cancel), for
     * callers whose confirmation needs the navigator or the activity at pop
     * time and therefore cannot capture them at push time — the callback is
     * serialized with the route stack, so it must resolve everything from its
     * parameters instead.
     */
    public static void showConfirmDialog(INavigator navigator,
                                         String title, String content, NavPopCallback<Activity> onPop) {
        Provider rootComponent = (Provider) navigator
                .getNavConfiguration().getRequiredComponent();
        NavExtDialogConfig navExtDialogConfig = rootComponent.get(NavExtDialogConfig.class);
        navigator.push(navExtDialogConfig.route_confirmDialog(),
                navExtDialogConfig.args_confirmDialog(title, content), onPop);
    }

    private static Boolean readConfirmResult(INavigator navigator, NavRoute navRoute) {
        Provider requiredComponent = (Provider) navigator
                .getNavConfiguration().getRequiredComponent();
        return requiredComponent.get(NavExtDialogConfig.class)
                .result_confirmDialog(navRoute);
    }

    public static void shareText(Context context, String textBody, String chooserMessage) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, textBody);
        shareIntent.setType("text/plain");
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
    }

    public static void shareFile(Context context, File file, String chooserMessage) {
        shareFile(context, file, chooserMessage, "*/*");
    }

    public static void shareFile(Context context, File file, String chooserMessage, String mime) {
        Uri fileUri =
                FileProvider.getUriForFile(
                        context,
                        Constants.FILE_PROVIDER_AUTHORITY,
                        file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        // the receiving app needs a temporary read grant for the content Uri
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setType(mime);
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
    }

    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getColorFromAttribute(Context context, int attribute) {
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attribute, typedValue, true);
        return typedValue.data;
    }

    private UiUtils() {
    }
}
