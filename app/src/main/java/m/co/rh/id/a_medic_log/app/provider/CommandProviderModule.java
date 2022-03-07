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
        providerRegistry.registerLazy(NewProfileCmd.class, () -> new NewProfileCmd(context, provider));
        providerRegistry.registerLazy(UpdateProfileCmd.class, () -> new UpdateProfileCmd(context, provider));
        providerRegistry.registerLazy(DeleteProfileCmd.class, () -> new DeleteProfileCmd(context, provider));
        providerRegistry.registerLazy(PagedProfileItemsCmd.class, () -> new PagedProfileItemsCmd(context, provider));
        providerRegistry.registerLazy(DeleteNoteCmd.class, () -> new DeleteNoteCmd(context, provider));
        providerRegistry.registerLazy(PagedNoteItemsCmd.class, () -> new PagedNoteItemsCmd(context, provider));
        providerRegistry.registerLazy(NewNoteCmd.class, () -> new NewNoteCmd(context, provider));
        providerRegistry.registerLazy(UpdateNoteCmd.class, () -> new UpdateNoteCmd(context, provider));
        providerRegistry.registerLazy(QueryNoteCmd.class, () -> new QueryNoteCmd(provider));
        providerRegistry.registerLazy(NewNoteTagCmd.class, () -> new NewNoteTagCmd(context, provider));
        providerRegistry.registerLazy(DeleteNoteTagCmd.class, () -> new DeleteNoteTagCmd(context, provider));
        providerRegistry.registerLazy(NewMedicineCmd.class, () -> new NewMedicineCmd(context, provider));
        providerRegistry.registerLazy(UpdateMedicineCmd.class, () -> new UpdateMedicineCmd(context, provider));
        providerRegistry.registerLazy(DeleteMedicineCmd.class, () -> new DeleteMedicineCmd(context, provider));
        providerRegistry.registerLazy(QueryMedicineCmd.class, () -> new QueryMedicineCmd(provider));
        providerRegistry.registerLazy(NewMedicineReminderCmd.class, () -> new NewMedicineReminderCmd(context, provider));
        providerRegistry.registerLazy(UpdateMedicineReminderCmd.class, () -> new UpdateMedicineReminderCmd(context, provider));
        providerRegistry.registerLazy(DeleteMedicineReminderCmd.class, () -> new DeleteMedicineReminderCmd(context, provider));
        providerRegistry.registerLazy(NewMedicineIntakeCmd.class, () -> new NewMedicineIntakeCmd(context, provider));
        providerRegistry.registerLazy(UpdateMedicineIntakeCmd.class, () -> new UpdateMedicineIntakeCmd(context, provider));
        providerRegistry.registerLazy(DeleteMedicineIntakeCmd.class, () -> new DeleteMedicineIntakeCmd(context, provider));
        providerRegistry.registerLazy(PagedMedicineIntakeItemsCmd.class, () -> new PagedMedicineIntakeItemsCmd(context, provider));
        providerRegistry.registerLazy(NewNoteAttachmentCmd.class, () -> new NewNoteAttachmentCmd(context, provider));
        providerRegistry.registerLazy(UpdateNoteAttachmentCmd.class, () -> new UpdateNoteAttachmentCmd(context, provider));
        providerRegistry.registerLazy(DeleteNoteAttachmentCmd.class, () -> new DeleteNoteAttachmentCmd(provider));
        providerRegistry.registerLazy(NewNoteAttachmentFileCmd.class, () -> new NewNoteAttachmentFileCmd(provider));
        providerRegistry.registerLazy(DeleteNoteAttachmentFileCmd.class, () -> new DeleteNoteAttachmentFileCmd(provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
