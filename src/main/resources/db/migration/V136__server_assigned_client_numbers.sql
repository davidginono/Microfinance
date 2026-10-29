-- Keep all existing identities. Only new public registrations use this sequence.
-- Serialize initialization with existing writers, without updating any client rows.
LOCK TABLE public.members IN SHARE ROW EXCLUSIVE MODE;
CREATE INDEX ix_members_member_no_upper ON public.members (upper(member_no));
CREATE SEQUENCE public.client_number_seq AS bigint MINVALUE 1 NO CYCLE CACHE 1;
SELECT setval('public.client_number_seq',
    greatest(coalesce(max(substring(member_no FROM 4)::bigint), 0) + 1, 1), false)
FROM public.members
WHERE upper(member_no) ~ '^CL-[0-9]{1,18}$';
