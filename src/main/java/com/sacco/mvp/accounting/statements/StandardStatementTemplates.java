package com.sacco.mvp.accounting.statements;

import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;

/** Starting mappings only. Saving/reviewing uses the existing complete-account validation. */
final class StandardStatementTemplates {
    private StandardStatementTemplates() { }
    static StatementDefinition create(Kind kind,List<Account> accounts) {
        var rows=new ArrayList<Row>();
        if(kind==Kind.CASH_FLOW){
            for(var section:List.of(Section.OPERATING,Section.INVESTING,Section.FINANCING))rows.add(heading(section));
        }else{
            var sections=switch(kind){
                case PROFIT_AND_LOSS->List.of(Section.INCOME,Section.EXPENSES);
                case BALANCE_SHEET->List.of(Section.ASSETS,Section.LIABILITIES,Section.EQUITY);
                case CHANGES_IN_EQUITY->List.of(Section.EQUITY);
                default->List.of(Section.NOTES);
            };
            for(var section:sections){
                rows.add(heading(section));
                var selected=accounts.stream().filter(a->switch(section){
                    case ASSETS->a.type().equals("ASSET");case LIABILITIES->a.type().equals("LIABILITY");
                    case EQUITY->Set.of("EQUITY","INCOME","EXPENSE").contains(a.type());
                    case INCOME->a.type().equals("INCOME");case EXPENSES->a.type().equals("EXPENSE");default->true;
                }).toList();
                var groups=new LinkedHashMap<String,List<UUID>>();
                for(var account:selected){String group=section==Section.EQUITY&&!account.type().equals("EQUITY")?"RESULT":account.purpose();groups.computeIfAbsent(group,k->new ArrayList<>()).add(account.id());}
                for(var group:groups.entrySet()){
                    var names=names(group.getKey());String id=section.name()+"_"+group.getKey();
                    rows.add(new Row(id,names[0],names[1],RowKind.ACCOUNT_GROUP,section,Unit.TZS,group.getValue(),Set.of(Section.INCOME,Section.LIABILITIES,Section.EQUITY).contains(section)?Sign.CREDIT_POSITIVE:Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null));
                }
            }
        }
        var title=switch(kind){case PROFIT_AND_LOSS->new String[]{"Profit and loss","Faida na hasara"};case BALANCE_SHEET->new String[]{"Balance sheet","Taarifa ya hali ya fedha"};case CASH_FLOW->new String[]{"Cash flow statement","Taarifa ya mtiririko wa fedha"};case CHANGES_IN_EQUITY->new String[]{"Statement of changes in equity","Taarifa ya mabadiliko ya mtaji"};default->new String[]{"Notes","Maelezo"};};
        return new StatementDefinition(1,kind==Kind.CASH_FLOW?2:1,kind,title[0],title[1],rows,List.of(),false);
    }
    private static Row heading(Section section){var names=switch(section){case ASSETS->new String[]{"Assets","Mali"};case LIABILITIES->new String[]{"Liabilities","Madeni"};case EQUITY->new String[]{"Equity","Mtaji"};case INCOME->new String[]{"Income","Mapato"};case EXPENSES->new String[]{"Expenses","Gharama"};case OPERATING->new String[]{"Cash flows from operating activities","Mtiririko wa fedha kutoka shughuli za uendeshaji"};case INVESTING->new String[]{"Cash flows from investing activities","Mtiririko wa fedha kutoka shughuli za uwekezaji"};case FINANCING->new String[]{"Cash flows from financing activities","Mtiririko wa fedha kutoka shughuli za ufadhili"};default->new String[]{"Notes","Maelezo"};};return new Row("HEADING_"+section,names[0],names[1],RowKind.HEADING,section,Unit.NONE,List.of(),null,null,null,null,true,false,false,null);}
    private static String[] names(String purpose){return switch(purpose){
        case "CASH"->new String[]{"Cash","Fedha taslimu"};case "BANK"->new String[]{"Bank balances","Salio la benki"};case "MOBILE_MONEY"->new String[]{"Mobile money","Fedha kwa simu"};
        case "LOAN_PRINCIPAL"->new String[]{"Loans receivable","Mikopo inayodaiwa"};case "INTEREST_RECEIVABLE"->new String[]{"Interest receivable","Riba inayodaiwa"};case "FEE_RECEIVABLE"->new String[]{"Fees receivable","Ada zinazodaiwa"};
        case "ALLOWANCE"->new String[]{"Loan-loss allowance","Posho ya hasara za mikopo"};case "FIXED_ASSET"->new String[]{"Fixed assets","Mali za kudumu"};case "PREPAYMENT"->new String[]{"Prepayments","Malipo ya awali"};
        case "PAYABLE"->new String[]{"Payables","Madeni yanayolipwa"};case "FUNDING"->new String[]{"Borrowings","Mikopo iliyopokelewa"};case "CAPITAL"->new String[]{"Owner capital","Mtaji wa wamiliki"};case "RESULT"->new String[]{"Accumulated results","Matokeo yaliyokusanywa"};
        case "INCOME"->new String[]{"Income","Mapato"};case "EXPENSE"->new String[]{"Expenses","Gharama"};case "TAX"->new String[]{"Taxes","Kodi"};case "CLEARING"->new String[]{"Clearing accounts","Akaunti za kusawazisha"};case "SUSPENSE"->new String[]{"Suspense accounts","Akaunti za muda"};case "INTERNAL_TRANSFER"->new String[]{"Internal transfers","Uhamisho wa ndani"};default->new String[]{"Other accounts","Akaunti nyingine"};};}
}
