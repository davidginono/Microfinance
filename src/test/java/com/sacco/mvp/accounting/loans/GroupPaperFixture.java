package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.grouplending.dto.GroupPaperDtos.PaperForm;
import com.sacco.mvp.grouplending.service.GroupPaperService;
import com.sacco.mvp.service.ApplicationClock;
import java.time.ZoneId;
import java.util.UUID;

final class GroupPaperFixture {
    private GroupPaperFixture() {}
    static UUID record(LoanRecordingPostgresTest f,UUID cycle,String kind,UUID client){
        var service=f.context.getBean(GroupPaperService.class);
        var target=service.targets(f.actor,cycle).stream().filter(t->t.kind().equals(kind)&&t.client().equals(client)).findFirst().orElseThrow();
        var form=new PaperForm();form.setTarget(target.key());form.setReference("Synthetic signed paper / "+target.key());form.setChecked(true);
        form.setSignedAt(f.context.getBean(ApplicationClock.class).now().atZoneSameInstant(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDateTime());
        form.setProvider("Synthetic bank");form.setAccountHolderName("Synthetic account holder");form.setAccountIdentifier("QA-"+client);
        form.setExpectedHash(service.preview(f.actor,cycle,form).hash());
        return service.record(f.actor,cycle,form,GroupOrganizationPostgresTest.documentFile());
    }
}
