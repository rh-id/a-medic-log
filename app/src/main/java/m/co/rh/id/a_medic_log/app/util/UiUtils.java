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
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class UiUtils {

    public static void showConfirmDialog(INavigator navigator, Provider provider,
                                         String title, String content, Runnable onConfirm) {
        NavExtDialogConfig navExtDialogConfig = provider.get(NavExtDialogConfig.class);
        navigator.push(navExtDialogConfig.route_confirmDialog(),
                navExtDialogConfig.args_confirmDialog(title, content),
                (navigator1, navRoute, activity, currentView) -> {
                    Provider requiredComponent = (Provider) navigator1
                            .getNavConfiguration().getRequiredComponent();
                    Boolean result = requiredComponent.get(NavExtDialogConfig.class)
                            .result_confirmDialog(navRoute);
                    if (result != null && result) {
                        onConfirm.run();
                    }
                });
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
