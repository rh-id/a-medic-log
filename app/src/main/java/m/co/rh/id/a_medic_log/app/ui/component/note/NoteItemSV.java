package m.co.rh.id.a_medic_log.app.ui.component.note;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.NoteDetailPage;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class NoteItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = NoteItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private SerialBehaviorSubject<Note> mNoteSubject;
    private DateFormat mDateFormat;

    public NoteItemSV() {
        mNoteSubject = new SerialBehaviorSubject<>(new Note());
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = provider.get(StatefulViewProvider.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_note, container, false);
        rootLayout.setOnClickListener(this);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        buttonEdit.setOnClickListener(this);
        buttonDelete.setOnClickListener(this);
        TextView textEntryDate = rootLayout.findViewById(R.id.text_entry_date);
        TextView textContent = rootLayout.findViewById(R.id.text_content);
        mSvProvider.get(RxDisposer.class).add("createView_onChangeNote",
                mNoteSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(note -> {
                            textEntryDate.setText(mDateFormat.format(note.entryDateTime));
                            textContent.setText(note.content);
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    public void setNote(Note note) {
        mNoteSubject.onNext(note);
    }

    public Note getNote() {
        return mNoteSubject.getValue();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_edit) {
            mNavigator.push(Routes.NOTE_DETAIL_PAGE,
                    NoteDetailPage.Args.forUpdate(mNoteSubject.getValue().id));
        } else if (id == R.id.button_delete) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_note);
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.getRoutePath(NavExtDialogConfig.ROUTE_CONFIRM),
                    navExtDialogConfig.args_confirmDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        Boolean result = provider.get(NavExtDialogConfig.class).result_confirmDialog(navRoute);
                        if (result != null && result) {
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(DeleteNoteCmd.class)
                                    .execute(mNoteSubject.getValue())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((note, throwable) -> {
                                        Context deleteContext = provider.getContext();
                                        if (throwable != null) {
                                            provider.get(ILogger.class)
                                                    .e(TAG,
                                                            deleteContext.getString(
                                                                    R.string.error_deleting_note),
                                                            throwable);
                                        } else {
                                            provider.get(ILogger.class)
                                                    .i(TAG,
                                                            deleteContext.getString(
                                                                    R.string.success_deleting_note));
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
        }
    }
}
