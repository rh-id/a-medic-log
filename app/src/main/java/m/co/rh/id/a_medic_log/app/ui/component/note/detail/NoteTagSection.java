package m.co.rh.id.a_medic_log.app.ui.component.note.detail;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.TreeSet;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteTagCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.NoteTagDetailSVDialog;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class NoteTagSection implements View.OnClickListener {

    private static final String TAG = NoteTagSection.class.getName();

    private final INavigator mNavigator;
    private final Provider mSvProvider;
    private final NoteState mNoteState;
    private final SerialBehaviorSubject<Boolean> mShowSubject;
    private final Long mRouteNoteId;
    private final ILogger mLogger;
    private final RxDisposer mRxDisposer;
    private final DeleteNoteTagCmd mDeleteNoteTagCmd;
    private final CompositeDisposable mCompositeDisposable;

    private TextView mNoteTagTitle;
    private ChipGroup mNoteTagChipGroup;

    public NoteTagSection(INavigator navigator, Provider svProvider,
                          NoteState noteState, SerialBehaviorSubject<Boolean> showSubject,
                          Long routeNoteId) {
        mNavigator = navigator;
        mSvProvider = svProvider;
        mNoteState = noteState;
        mShowSubject = showSubject;
        mRouteNoteId = routeNoteId;
        mLogger = svProvider.get(ILogger.class);
        mRxDisposer = svProvider.get(RxDisposer.class);
        mDeleteNoteTagCmd = svProvider.get(DeleteNoteTagCmd.class);
        mCompositeDisposable = new CompositeDisposable();
    }

    public void bindViews(Activity activity, ViewGroup rootLayout) {
        Button expandNoteTag = rootLayout.findViewById(R.id.button_expand_note_tag);
        View noteTagTextContainer = rootLayout.findViewById(R.id.container_note_tag_text);
        Button addNoteTagButton = rootLayout.findViewById(R.id.button_add_note_tag);
        mNoteTagTitle = rootLayout.findViewById(R.id.text_note_tag_title);
        mNoteTagChipGroup = rootLayout.findViewById(R.id.chip_group_note_tag);
        expandNoteTag.setOnClickListener(this);
        noteTagTextContainer.setOnClickListener(this);
        addNoteTagButton.setOnClickListener(this);
        mRxDisposer.add("NoteTagSection.bindViews_onNoteTagChanged",
                mNoteState.getNoteTagSetFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(noteTags -> {
                            mNoteTagTitle.setText(activity.getString(R.string.title_tag, noteTags.size()));
                            mNoteTagChipGroup.removeAllViews();
                            if (!noteTags.isEmpty()) {
                                boolean isUpdate = isUpdate();
                                for (NoteTag noteTag : noteTags) {
                                    Chip chip = new Chip(activity);
                                    chip.setText(noteTag.tag);
                                    chip.setOnCloseIconClickListener(view -> {
                                        mNoteTagChipGroup.removeView(chip);
                                        chip.setOnCloseIconClickListener(null);
                                        TreeSet<NoteTag> noteTagSet = mNoteState.getNoteTagSet();
                                        noteTagSet.remove(noteTag);
                                        mNoteTagTitle.setText(activity.getString(R.string.title_tag, noteTagSet.size()));
                                        if (isUpdate && noteTag.id != null) {
                                            Context context = activity.getApplicationContext();
                                            mCompositeDisposable.add(mDeleteNoteTagCmd.execute(noteTag)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((deletedNoteTag, throwable) -> {
                                                        String successMessage = context.getString(R.string.success_deleting_note_tag);
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) {
                                                                cause = throwable;
                                                            }
                                                            mLogger
                                                                    .e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger
                                                                    .i(TAG, successMessage);
                                                        }
                                                    }));
                                        }
                                    });
                                    chip.setCloseIconVisible(true);
                                    mNoteTagChipGroup.addView(chip);
                                }
                            }
                        }));
        mRxDisposer.add("NoteTagSection.bindViews_onNoteTagShow",
                mShowSubject.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                mNoteTagChipGroup.setVisibility(View.VISIBLE);
                            } else {
                                mNoteTagChipGroup.setVisibility(View.GONE);
                            }
                            expandNoteTag.setActivated(aBoolean);
                        }));
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_note_tag) {
            NoteTagDetailSVDialog.Args args;
            if (isUpdate()) {
                args = NoteTagDetailSVDialog.Args.save(mRouteNoteId);
            } else {
                args = NoteTagDetailSVDialog.Args.dontSave();
            }
            mNavigator.push(Routes.NOTE_TAG_DETAIL_DIALOG,
                    args,
                    (navigator, navRoute, activity, currentView) -> {
                        NoteTagDetailSVDialog.Result result = NoteTagDetailSVDialog.Result.of(navRoute);
                        if (result != null) {
                            mNoteState.addNoteTag(result.getNoteTag());
                        }
                    });
        } else if (id == R.id.container_note_tag_text || id == R.id.button_expand_note_tag) {
            mShowSubject.onNext(!mShowSubject.getValue());
        }
    }

    private boolean isUpdate() {
        return mRouteNoteId != null;
    }

    public void dispose() {
        mCompositeDisposable.dispose();
        mNoteTagTitle = null;
        mNoteTagChipGroup = null;
    }
}
