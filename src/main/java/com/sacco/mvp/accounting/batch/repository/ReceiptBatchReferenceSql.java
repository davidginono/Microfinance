package com.sacco.mvp.accounting.batch.repository;

/** Read-side names for batch receipts. Posting and correction retain their recorded references. */
public final class ReceiptBatchReferenceSql {
    private ReceiptBatchReferenceSql() {}

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String POST_ITEM_ID = "substring(p.reference from '^BATCH/(" + UUID_PATTERN + ")/[0-9]+$')::uuid";
    public static final String RECEIPT_ITEM_ID = "substring(r.reference from '^BATCH/(" + UUID_PATTERN + ")$')::uuid";
    public static final String RECEIPT_JOINS = """
        left join receipt_batch_item receipt_item on receipt_item.id=%s
            and receipt_item.receipt_id=coalesce(r.reverses_id,r.id)
            and receipt_item.sacco_id=r.sacco_id and receipt_item.station_id=r.station_id
        left join receipt_batch receipt_batch_source on receipt_batch_source.id=receipt_item.batch_id
            and receipt_batch_source.sacco_id=r.sacco_id and receipt_batch_source.station_id=r.station_id
        """.formatted(RECEIPT_ITEM_ID);
    public static final String RECEIPT_REFERENCE = """
        case when %s is null then r.reference
             else coalesce(receipt_batch_source.title||' / '||receipt_item.line_no,r.number) end
        """.formatted(RECEIPT_ITEM_ID);
}
