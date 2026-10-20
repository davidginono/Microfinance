package com.sacco.mvp.support;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Explicit table ordering applied by repositories before their existing bounded pagination. */
public class ResourceTableSort implements QueryRewriter {
    private static final String TABLE_CONTEXT = ResourceTableSort.class.getName()+".tables";
    private static final Pattern TOKENS = Pattern.compile("'([^']|'')*'|\"[^\"]*\"|[()]|\\border\\s+by\\b|\\blimit\\b|\\boffset\\b|\\bfetch\\b|\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE);
    private record Columns(Set<String> tables, Map<String, String> fields, String id, String wrapper) {
        Columns(Set<String> tables, Map<String, String> fields, String id) { this(tables,fields,id,null); }
    }
    private static final String PRODUCT_NAME="coalesce(nullif(p.productName,''),case cast(l.loanType as string) when 'LOAN_ADVANCE' then 'Loan Advance (Mkopo wa Chapchap)' when 'EDUCATION_LOAN' then 'Education Loan (Mkopo wa Elimu)' when 'EMERGENCY_LOAN' then 'Emergency Loan (Mkopo wa Dharura)' when 'DEVELOPMENT_LOAN' then 'Development Loan (Mkopo wa Biashara)' else 'Loan Product' end)";
    private static final String PRODUCT_NAME_SQL="coalesce(nullif(p.product_name,''),case l.loan_type when 'LOAN_ADVANCE' then 'Loan Advance (Mkopo wa Chapchap)' when 'EDUCATION_LOAN' then 'Education Loan (Mkopo wa Elimu)' when 'EMERGENCY_LOAN' then 'Emergency Loan (Mkopo wa Dharura)' when 'DEVELOPMENT_LOAN' then 'Development Loan (Mkopo wa Biashara)' else 'Loan Product' end)";
    private static final Map<String, Columns> QUERIES = queries();
    private static Map<String, Columns> queries() {
        var queries = new java.util.HashMap<String, Columns>();
        queries.put(
        "loan-queue", new Columns(Set.of("manager-queue-1", "accountant-queue-1", "disbursement-queue-1"), Map.of(
            "number", "l.applicationNumber", "product", PRODUCT_NAME, "applicant", "m.fullName",
            "amount", "l.amount", "status", "l.status", "date", "coalesce(l.disbursementDate, cast(l.createdAt as LocalDate))"), "l.id"));
        queries.put("users-native", new Columns(Set.of("admin-users-1"), userColumns(), "m.id"));
        queries.put("members", new Columns(Set.of("admin-sacco-detail-2"), userColumns(), "m.id"));
        queries.put("staff-archives", new Columns(Set.of("manager-archive-1","accountant-archive-1","disbursement-archive-1"), Map.of("number","l.application_number","loan","l.loan_id","product",PRODUCT_NAME_SQL,"applicant","m.full_name","amount","l.amount","decision","r.decision","reason","r.reasons","date","r.created_at","disbursed","l.disbursement_date","status","l.status"),"r.id"));
        queries.put("board-archives", new Columns(Set.of("board-assigned-1"), Map.of("number","l.application_number","product",PRODUCT_NAME_SQL,"applicant","m.full_name","amount","l.amount","status","l.status","decision","r.decision","reason","r.comment","date","coalesce(r.decided_at,r.created_at)"),"r.id"));
        queries.put("incidents", new Columns(Set.of("admin-incidents-1"), Map.of("subject","i.subject","category","i.category","status","i.status","date","i.createdAt"),"i.id"));
        queries.put("support-archives", new Columns(Set.of("app-support-archive-1","admin-support-archive-1"), Map.of("subject","i.subject","message","i.message","status","i.status","read","case when exists (select 1 from notifications n where n.type='SUPPORT_MESSAGE' and n.read_at is not null and n.payload->'details'->>'incidentId'=cast(i.id as text)) then 1 else 0 end","date","i.created_at"),"i.id"));
        queries.put("audit", new Columns(Set.of("admin-audit-1","admin-events-1"), Map.of("action","coalesce(nullif(al.action_description,''),al.action)","institution","coalesce((select rs.sacco_name from registered_saccos rs where rs.sacco_id=al.sacco_id),al.sacco_id)","status","coalesce(al.event_status,'SUCCESS')","entity","al.entity_type","reference","coalesce(nullif(al.reference_value,''),cast(al.entity_id as text))","actor","al.actor_member_id","name","coalesce((select m.full_name from members m where m.id=al.actor_member_id),'System')","date","al.created_at"),"al.id","al"));
        queries.put("outbox", new Columns(Set.of("admin-outbox-1"), Map.of("event","oe.event_type","aggregate","oe.aggregate_type","status","oe.status","date","oe.created_at","actor","coalesce(nullif(oe.payload->>'actorId',''),nullif(oe.payload->'details'->>'actorId',''),nullif(oe.payload->'details'->>'managerId',''),nullif(oe.payload->'details'->>'applicantMemberId',''),nullif(oe.payload->'details'->>'requesterMemberId',''))","name","(select m.full_name from members m where cast(m.id as text)=coalesce(nullif(oe.payload->>'actorId',''),nullif(oe.payload->'details'->>'actorId',''),nullif(oe.payload->'details'->>'managerId',''),nullif(oe.payload->'details'->>'applicantMemberId',''),nullif(oe.payload->'details'->>'requesterMemberId','')))"),"oe.id","oe"));
        queries.put("notifications", new Columns(Set.of("admin-notifications-1","app-notifications-1","manager-notifications-1","board-notifications-1","accountant-notifications-1","disbursement-notifications-1","app-support-replies-1","app-support-replies-2","admin-support-replies-1","admin-support-replies-2","admin-messages-1"), Map.of("subject","coalesce(nullif(n.payload->>'subject',''),n.type)","message","n.payload->>'message'","source","coalesce(nullif(n.payload->>'source',''),nullif(n.payload->>'senderName',''),'System')","type","n.type","date","n.created_at","category","(select i.category from admin_incidents i where cast(i.id as text)=n.payload->'details'->>'incidentId')","status","(select i.status from admin_incidents i where cast(i.id as text)=n.payload->'details'->>'incidentId')"),"n.id"));
        queries.put("ledger-loans", new Columns(Set.of("repayments-index-1"), Map.of("loan","l.loanId","date","l.disbursementDate","outstanding","case when a.status=com.sacco.mvp.domain.LoanStatus.DISBURSEMENT_REVERSED then 0 else l.principal-l.principalPaid end","principal","l.principalPaid","interest","l.interestPaid"),"l.loanApplicationId"));
        queries.put("receipt-history", new Columns(Set.of("repayments-loan-1"), Map.of("reference","t.receiptReference","date","t.paymentDate","status","case when exists(select r.id from LoanRepaymentTransaction r where r.reversesTransactionId=t.id) then 'REVERSED' else cast(t.kind as string) end","amount","t.amount","principal","t.principalAmount","interest","t.interestAmount"),"t.id"));
        queries.put("loan-journal", new Columns(Set.of("repayments-loan-3"), Map.of("date","j.effectiveDate","account","j.accountCode","debit","j.debit","credit","j.credit"),"j.id"));
        queries.put("board-queue", new Columns(Set.of("board-assigned-1"), Map.of("number","l.applicationNumber","product",PRODUCT_NAME,"applicant","m.fullName","amount","l.amount","status","l.status","decision","r.decision","reason","r.comment","date","r.createdAt"),"r.id"));
        queries.put("guarantees", new Columns(Set.of("app-guarantee-requests-1","app-archives-1"), Map.of("reference","g.loanApplicationId","applicant","m.fullName","product",PRODUCT_NAME,"amount","l.amount","status","g.status","date","coalesce(g.decidedAt,g.createdAt)"),"g.id"));
        queries.put("guaranteed-loans", new Columns(Set.of("app-guaranteed-loans-1"), Map.of("reference","coalesce(l.loanId,cast(l.applicationNumber as string))","applicant","m.fullName","product",PRODUCT_NAME,"amount","l.amount","status","l.status","due","l.finalDueDate","remaining","l.finalDueDate"),"g.id"));
        queries.put("member-archives", new Columns(Set.of("app-archives-2"), Map.of("number","l.applicationNumber","loan","l.loanId","product",PRODUCT_NAME,"amount","l.amount","status","l.status","date","case when l.status=com.sacco.mvp.domain.LoanStatus.PAID and l.paidAt is not null then l.paidAt else l.updatedAt end"),"l.id"));
        queries.put("product-configuration", new Columns(Set.of("chairperson-configurations-1"), Map.of("code","coalesce(nullif(p.product_code,''),case p.loan_type when 'LOAN_ADVANCE' then 'ADV_LOAN' when 'EDUCATION_LOAN' then 'EDU_LOAN' when 'EMERGENCY_LOAN' then 'EMERGENCY_LOAN' when 'DEVELOPMENT_LOAN' then 'DEV_LOAN' else 'CUSTOM_LOAN' end)","name","coalesce(nullif(p.product_name,''),case p.loan_type when 'LOAN_ADVANCE' then 'Loan Advance (Mkopo wa Chapchap)' when 'EDUCATION_LOAN' then 'Education Loan (Mkopo wa Elimu)' when 'EMERGENCY_LOAN' then 'Emergency Loan (Mkopo wa Dharura)' when 'DEVELOPMENT_LOAN' then 'Development Loan (Mkopo wa Biashara)' else 'Loan Product' end)","status","coalesce(p.product_status,case when p.active then 'ACTIVE' else 'SUSPENDED' end)","amount","row(p.minimum_amount,p.maximum_amount)","workflow","(select string_agg(v.label,' > ' order by v.priority,v.label) from (values (case when p.manager_priority>0 then p.manager_priority when p.workflow_start_stage='LOAN_OFFICER' then 2 else 1 end,'Branch Manager',coalesce(p.manager_review_required,true)),(case when p.loan_officer_priority>0 then p.loan_officer_priority when p.workflow_start_stage='LOAN_OFFICER' then 1 else 2 end,'Loan Officer',coalesce(p.loan_officer_review_required,false)),(case when p.chairperson_priority between 1 and 7 then p.chairperson_priority else 3 end,'Chairperson',coalesce(p.chairperson_review_required,false)),(case when p.board_priority between 1 and 7 then p.board_priority else 3 end,'Board Member',coalesce(p.board_review_required,false)),(case when p.committee_priority between 1 and 7 then p.committee_priority else 4 end,'Credit Committee',coalesce(p.committee_review_required,true)),(case when p.accountant_priority between 1 and 7 then p.accountant_priority else 5 end,'Accountant',coalesce(p.accountant_review_required,true)),(7,'Disbursement/Teller Officer',coalesce(p.disbursement_officer_required,true))) v(priority,label,required) where v.required)"),"p.id"));
        queries.put("processed-loans", new Columns(Set.of("chairperson-processed-loans-1"), Map.of("number","l.application_number","loan","l.loan_id","applicant","m.full_name","product",PRODUCT_NAME_SQL,"amount","l.amount","status","l.status","decision","(select d.decision from (select r.loan_application_id,case when r.decision='REJECT' then 'REJECTED' when r.review_stage='DISBURSEMENT_OFFICER' then 'DISBURSED' else 'APPROVED' end decision,r.created_at decided,r.id from manager_reviews r union all select b.loan_application_id,case when b.decision='REJECTED' then 'REJECTED' else 'APPROVED' end,coalesce(b.decided_at,b.created_at),b.id from board_reviews b where b.decision<>'PENDING') d where d.loan_application_id=l.id order by d.decided desc,d.id limit 1)","decisions","((select count(*) from manager_reviews r where r.loan_application_id=l.id)+(select count(*) from board_reviews b where b.loan_application_id=l.id and b.decision<>'PENDING'))"),"l.id"));
        queries.put("sms-accounts", new Columns(Set.of("admin-sms-usage-1","admin-sacco-detail-4"), Map.of("institution","i.sacco_name","branch","a.station_id","available","a.available_units","reserve","a.alert_reserved_units","baseline","a.warning_baseline","alerts","a.depleted_alert_sms_sent_count","status","a.status"),"a.id"));
        queries.put("sms-history", new Columns(Set.of("admin-sacco-detail-3"), Map.of("date","s.createdAt","event","s.eventType","outcome","s.outcome","units","s.unitChange","reference","s.providerReference","note","s.note"),"s.id"));
        queries.put("loan-sms", new Columns(Set.of("admin-sms-usage-2"), Map.of("applicant","applicantName","client","applicantMemberNo","loan","loanId","number","applicationNumber","status","loanStatus","institution","(select i.saccoName from RegisteredSacco i where i.saccoId=app.saccoId)","branch","stationId","events","smsEventCount","units","unitsUsed","date","lastSmsAt"),"ledger.loanApplicationId"));
        return Map.copyOf(queries);
    }
    private static Map<String,String> userColumns() {
        String member="coalesce(m.is_member,m.position='MEMBER')";
        String staff="(m.position<>'MEMBER' or exists(select 1 from member_staff_roles sr where sr.member_id=m.id and sr.role_name<>'MEMBER'))";
        String roles="coalesce((select string_agg(labels.label,', ' order by labels.priority) from (select sr.role_name role from member_staff_roles sr where sr.member_id=m.id union select m.position) assigned join (values ('ADMIN','Super Admin',0),('MINOR_ADMIN','Institution Admin',1),('LOAN_OFFICER','Loan Officer',2),('MANAGER','Manager',3),('ACCOUNTANT','Accountant',4),('DISBURSEMENT_OFFICER','Disbursement/Teller Officer',5),('CHAIRPERSON','Chairperson',6),('BOARD','Board',7),('CREDIT_COMMITTEE','Credit Committee',8)) labels(role,label,priority) on labels.role=assigned.role),case when "+member+" then 'MEMBER' else '' end)";
        return Map.of("id","m.id","client","case when "+member+" then m.member_no else '-' end",
            "staff","coalesce(nullif(m.staff_no,''),case when not "+member+" or "+staff+" then m.member_no else '-' end)",
            "name","m.full_name","email","m.email","phone","m.phone","roles",roles,
            "membership","case when not "+member+" then 'Staff' when "+staff+" then 'Staff And Member' else 'Member' end","status","m.status");
    }

    private final Columns columns;
    private ResourceTableSort(String query) { columns = QUERIES.get(query); }
    public static final class LoanQueue extends ResourceTableSort { public LoanQueue() { super("loan-queue"); } }
    public static final class UsersNative extends ResourceTableSort { public UsersNative() { super("users-native"); } }
    public static final class Members extends ResourceTableSort { public Members() { super("members"); } }

    public static final class StaffArchives extends ResourceTableSort { public StaffArchives() { super("staff-archives"); } }

    public static final class BoardArchives extends ResourceTableSort { public BoardArchives() { super("board-archives"); } }

    public static final class Incidents extends ResourceTableSort { public Incidents() { super("incidents"); } }

    public static final class SupportArchives extends ResourceTableSort { public SupportArchives() { super("support-archives"); } }

    public static final class Audit extends ResourceTableSort { public Audit() { super("audit"); } }

    public static final class Outbox extends ResourceTableSort { public Outbox() { super("outbox"); } }

    public static final class Notifications extends ResourceTableSort { public Notifications() { super("notifications"); } }

    public static final class LedgerLoans extends ResourceTableSort { public LedgerLoans() { super("ledger-loans"); } }

    public static final class ReceiptHistory extends ResourceTableSort { public ReceiptHistory() { super("receipt-history"); } }

    public static final class LoanJournal extends ResourceTableSort { public LoanJournal() { super("loan-journal"); } }

    public static final class BoardQueue extends ResourceTableSort { public BoardQueue() { super("board-queue"); } }

    public static final class Guarantees extends ResourceTableSort { public Guarantees() { super("guarantees"); } }

    public static final class GuaranteedLoans extends ResourceTableSort { public GuaranteedLoans() { super("guaranteed-loans"); } }

    public static final class MemberArchives extends ResourceTableSort { public MemberArchives() { super("member-archives"); } }

    public static final class ProductConfiguration extends ResourceTableSort { public ProductConfiguration() { super("product-configuration"); } }

    public static final class ProcessedLoans extends ResourceTableSort { public ProcessedLoans() { super("processed-loans"); } }

    public static final class SmsAccounts extends ResourceTableSort { public SmsAccounts() { super("sms-accounts"); } }

    public static final class SmsHistory extends ResourceTableSort { public SmsHistory() { super("sms-history"); } }

    public static final class LoanSms extends ResourceTableSort { public LoanSms() { super("loan-sms"); } }

    private static String requested(String table, Map<String, String> columns, String id) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        var request = attributes.getRequest();
        if (!"GET".equals(request.getMethod())) return null;
        if (request.getAttribute(TABLE_CONTEXT) instanceof Set<?> tables && !tables.contains(table)) return null;
        String value = request.getParameter("tableSort[" + table + "]");
        if (value == null) return null;
        String column = columns.get(value);
        if (column == null) return null;
        String direction = "desc".equals(request.getParameter("tableDirection[" + table + "]")) ? "desc" : "asc";
        return column + " " + direction + " nulls last," + id + " " + direction;
    }

    public static String order(String table, Map<String, String> columns, String id, String fallback) {
        String order = requested(table, columns, id);
        return order == null ? fallback : order;
    }

    /** Distinguishes complete tables that share one repository query in the same request. */
    public static <T> T forTables(Set<String> tables, java.util.function.Supplier<T> query) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return query.get();
        var request=attributes.getRequest();Object previous=request.getAttribute(TABLE_CONTEXT);
        request.setAttribute(TABLE_CONTEXT,tables);
        try { return query.get(); }
        finally { if(previous==null)request.removeAttribute(TABLE_CONTEXT);else request.setAttribute(TABLE_CONTEXT,previous); }
    }

    /** Complete computed datasets only; call before taking any display page. */
    public static <T> List<T> rows(String table, List<T> rows, Map<String, Comparator<T>> columns, Comparator<T> tie) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return rows;
        if (!"GET".equals(attributes.getRequest().getMethod())) return rows;
        String field = attributes.getRequest().getParameter("tableSort[" + table + "]");
        if (field == null) return rows;
        Comparator<T> order = columns.get(field);
        if (order == null) return rows;
        if ("desc".equals(attributes.getRequest().getParameter("tableDirection[" + table + "]"))) order = order.reversed();
        return rows.stream().sorted(order.thenComparing(tie)).toList();
    }

    @Override
    public String rewrite(String query, Sort sort) {
        for (String table : columns.tables()) {
            String order = requested(table, columns.fields(), columns.id());
            if (order == null) continue;
            // Only replace the outer ordering. Keep nested latest-record queries and SQL limits intact.
            var tokens = TOKENS.matcher(query);
            int depth = 0, start = -1, tail = query.length();
            while (tokens.find()) {
                String token = tokens.group().toLowerCase(java.util.Locale.ROOT);
                if (token.equals("(")) depth++;
                else if (token.equals(")")) depth--;
                else if (depth == 0 && token.startsWith("order")) start = tokens.start();
                else if (depth == 0 && start >= 0 && !token.startsWith("'") && !token.startsWith("\"")) {tail=tokens.start();break;}
            }
            if (start >= 0) {
                String source = query.substring(0,start);
                if (columns.wrapper() != null) source = "select * from (" + source + ") " + columns.wrapper() + " ";
                return source + "order by " + order + " " + query.substring(tail);
            }
        }
        return query;
    }
}
