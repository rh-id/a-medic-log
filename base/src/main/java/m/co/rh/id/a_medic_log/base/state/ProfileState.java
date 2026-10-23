package m.co.rh.id.a_medic_log.base.state;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Rich profile aggregate fired by ProfileChangeNotifier; on deletion the note
 * list is the pre-delete snapshot with every note's nested state (rows are
 * cascade-removed once the delete commits)
 */
public class ProfileState implements Serializable, Cloneable {
    private SerialBehaviorSubject<Profile> mProfileSubject;
    private SerialBehaviorSubject<ArrayList<NoteState>> mNoteListSubject;

    public ProfileState() {
        mProfileSubject = new SerialBehaviorSubject<>(new Profile());
        mNoteListSubject = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public ProfileState(Profile profile) {
        mProfileSubject = new SerialBehaviorSubject<>(profile);
        mNoteListSubject = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateProfile(Profile profile) {
        mProfileSubject.onNext(profile);
    }

    public Profile getProfile() {
        return mProfileSubject.getValue();
    }

    public void updateNoteList(Collection<NoteState> noteStates) {
        mNoteListSubject.onNext(new ArrayList<>(noteStates));
    }

    public ArrayList<NoteState> getNoteList() {
        return mNoteListSubject.getValue();
    }

    public String getProfileName() {
        return getProfile().name;
    }

    @Override
    public ProfileState clone() {
        ProfileState profileState = new ProfileState();
        Profile profile = mProfileSubject.getValue();
        if (profile != null) {
            profile = profile.clone();
        }
        profileState.updateProfile(profile);
        ArrayList<NoteState> noteStates = mNoteListSubject.getValue();
        if (noteStates != null && !noteStates.isEmpty()) {
            ArrayList<NoteState> noteStatesClone = new ArrayList<>(noteStates.size());
            for (NoteState noteState : noteStates) {
                noteStatesClone.add(noteState.clone());
            }
            noteStates = noteStatesClone;
        } else {
            noteStates = new ArrayList<>();
        }
        profileState.updateNoteList(noteStates);
        return profileState;
    }
}
