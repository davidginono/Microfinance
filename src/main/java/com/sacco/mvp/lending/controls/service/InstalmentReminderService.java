package com.sacco.mvp.lending.controls.service;

import com.sacco.mvp.lending.controls.repository.CreditControlRepository;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service @RequiredArgsConstructor
public class InstalmentReminderService {
    private final CreditControlRepository records;
    private final ApplicationClock clock;
    private final MessageSource messages;
    private final ObjectMapper json;
    /** Queues evidence and outbox atomically; delivery runs in the existing worker. */
    @Transactional(timeout=30) public int queueBatch(){
        var day=clock.today();var rows=records.reminders(day,200);
        for(var r:rows){
            var locale=Locale.forLanguageTag("sw".equals(r.language())?"sw":"en");
            String subject=messages.getMessage("credit.reminder.subject",null,locale);
            String message=messages.getMessage("credit.reminder.message",new Object[]{r.number(),r.due().toString(),r.amount().toPlainString()},locale);
            var details=Map.of("loanId",r.loan().toString(),"installmentId",r.installment().toString(),"dueDate",r.due().toString(),"amount",r.amount(),"reminderDate",day.toString());
            var payload=Map.of("recipientId",r.client().toString(),"saccoId",r.institution(),"stationId",r.branch(),"subject",subject,"message",message,"details",details);
            records.reminder(r,UUID.randomUUID(),day,json.writeValueAsString(payload),clock.now());
        }return rows.size();
    }
}
