package m.co.rh.id.a_medic_log.app.provider;

import android.content.Context;

import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteAttachmentFileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteNoteTagCmd;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteProfileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteAttachmentFileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewNoteTagCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewProfileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicineIntakeItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.command.PagedNoteItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.command.PagedProfileItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryProfileCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateNoteAttachmentCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateProfileCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class CommandProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewProfileCmd.class, () -> new NewProfileCmd(provider));
        providerRegistry.registerLazy(UpdateProfileCmd.class, () -> new UpdateProfileCmd(provider));
        providerRegistry.registerLazy(DeleteProfileCmd.class, () -> new DeleteProfileCmd(provider));
        providerRegistry.registerLazy(PagedProfileItemsCmd.class, () -> new PagedProfileItemsCmd(provider));
        providerRegistry.registerLazy(QueryProfileCmd.class, () -> new QueryProfileCmd(provider));
        providerRegistry.registerLazy(DeleteNoteCmd.class, () -> new DeleteNoteCmd(provider));
        providerRegistry.registerLazy(PagedNoteItemsCmd.class, () -> new PagedNoteItemsCmd(provider));
        providerRegistry.registerLazy(NewNoteCmd.class, () -> new NewNoteCmd(provider));
        providerRegistry.registerLazy(UpdateNoteCmd.class, () -> new UpdateNoteCmd(provider));
        providerRegistry.registerLazy(QueryNoteCmd.class, () -> new QueryNoteCmd(provider));
        providerRegistry.registerLazy(NewNoteTagCmd.class, () -> new NewNoteTagCmd(provider));
        providerRegistry.registerLazy(DeleteNoteTagCmd.class, () -> new DeleteNoteTagCmd(provider));
        providerRegistry.registerLazy(NewMedicineCmd.class, () -> new NewMedicineCmd(provider));
        providerRegistry.registerLazy(UpdateMedicineCmd.class, () -> new UpdateMedicineCmd(provider));
        providerRegistry.registerLazy(DeleteMedicineCmd.class, () -> new DeleteMedicineCmd(provider));
        providerRegistry.registerLazy(QueryMedicineCmd.class, () -> new QueryMedicineCmd(provider));
        providerRegistry.registerLazy(NewMedicineReminderCmd.class, () -> new NewMedicineReminderCmd(provider));
        providerRegistry.registerLazy(UpdateMedicineReminderCmd.class, () -> new UpdateMedicineReminderCmd(provider));
        providerRegistry.registerLazy(DeleteMedicineReminderCmd.class, () -> new DeleteMedicineReminderCmd(provider));
        providerRegistry.registerLazy(NewMedicineIntakeCmd.class, () -> new NewMedicineIntakeCmd(provider));
        providerRegistry.registerLazy(UpdateMedicineIntakeCmd.class, () -> new UpdateMedicineIntakeCmd(provider));
        providerRegistry.registerLazy(DeleteMedicineIntakeCmd.class, () -> new DeleteMedicineIntakeCmd(provider));
        providerRegistry.registerLazy(PagedMedicineIntakeItemsCmd.class, () -> new PagedMedicineIntakeItemsCmd(provider));
        providerRegistry.registerLazy(NewNoteAttachmentCmd.class, () -> new NewNoteAttachmentCmd(provider));
        providerRegistry.registerLazy(UpdateNoteAttachmentCmd.class, () -> new UpdateNoteAttachmentCmd(provider));
        providerRegistry.registerLazy(DeleteNoteAttachmentCmd.class, () -> new DeleteNoteAttachmentCmd(provider));
        providerRegistry.registerLazy(NewNoteAttachmentFileCmd.class, () -> new NewNoteAttachmentFileCmd(provider));
        providerRegistry.registerLazy(DeleteNoteAttachmentFileCmd.class, () -> new DeleteNoteAttachmentFileCmd(provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
