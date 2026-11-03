-- A built-in bilingual message replaces branch notice publication. This is a data-use
-- acknowledgement, not evidence of an approved institutional retention policy.
-- Keep earlier notice revisions and accepted snapshots unchanged.
CREATE FUNCTION client_onboarding_data_notice(notice_version text DEFAULT 'MICROFINANCE_DATA_USE_V1')
RETURNS TABLE(id uuid,version text,english text,kiswahili text) LANGUAGE sql IMMUTABLE AS $$
 SELECT '59bf1107-d9c1-44ca-a91f-e4b266123d3c'::uuid,'MICROFINANCE_DATA_USE_V1'::text,
  'We protect your personal information and restrict access to authorised people. We use your information and documents for identity checks, loan assessment, loan and repayment administration, fraud prevention, required records and client support. We share it only with authorised service providers or as required by law, and retain it only as needed or legally required. Contact your branch to request access or corrections, withdraw consent, or raise a privacy concern.'::text,
  'Tunalinda taarifa zako binafsi na kuruhusu watu walioidhinishwa tu kuzifikia. Tunatumia taarifa na nyaraka zako kuhakiki utambulisho, kutathmini maombi ya mkopo, kusimamia mikopo na marejesho, kuzuia udanganyifu, kutunza kumbukumbu zinazohitajika na kutoa huduma kwa wateja. Tunashiriki taarifa na watoa huduma walioidhinishwa au kama sheria inavyotaka, na kuzihifadhi kwa muda unaohitajika kwa huduma hizi au kisheria. Wasiliana na tawi lako kuomba kuona au kusahihisha taarifa zako, kuondoa ridhaa au kutoa malalamiko ya faragha.'::text
 WHERE notice_version='MICROFINANCE_DATA_USE_V1'
$$;

CREATE OR REPLACE FUNCTION client_onboarding_complete(revision uuid,day date) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT coalesce((SELECT mode='SUBMITTED' AND length(identity_key)=64
  AND profile->>'identityType' IN ('NATIONAL_ID','PASSPORT','VOTER_ID','DRIVING_LICENCE','OTHER')
  AND length(trim(profile->>'identityNumber'))>=4 AND length(profile->>'issuingCountry')=2
  AND (profile->>'phone') ~ '^255[0-9]{9}$'
  AND (profile->>'birthDate')::date BETWEEN '1901-01-01'::date AND day
  AND (((profile->>'identityNoExpiry')::boolean AND profile->>'identityExpiresOn' IS NULL)
       OR (NOT (profile->>'identityNoExpiry')::boolean AND (profile->>'identityExpiresOn')::date>=day))
  AND profile->>'lawfulBasis' IN ('CONSENT','CONTRACT','LEGAL_OBLIGATION')
  AND (profile->>'consentOn')::date BETWEEN '1901-01-01'::date AND day
  AND NOT EXISTS(SELECT 1 FROM unnest(ARRAY['fullName','address','activity','activityAddress','sourceOfFunds','noticeVersion','consentReference']) k WHERE coalesce(length(trim(profile->>k)),0)=0)
  AND CASE WHEN profile->>'noticeId'='59bf1107-d9c1-44ca-a91f-e4b266123d3c' THEN
    EXISTS(SELECT 1 FROM client_onboarding_data_notice(profile->>'noticeVersion') n
      WHERE n.id::text=profile->>'noticeId' AND n.english=profile->>'noticeEnglish' AND n.kiswahili=profile->>'noticeKiswahili'
      AND profile->>'lawfulBasis'='CONSENT'
      AND CASE WHEN coalesce((profile->>'selfService')::boolean,false) THEN
        profile->>'consentReference'='Client acknowledgement / '||n.id::text
        AND (profile->>'acknowledgedAt')::timestamptz<=r.recorded_at
        AND ((profile->>'acknowledgedAt')::timestamptz AT TIME ZONE 'Africa/Dar_es_Salaam')::date=(profile->>'consentOn')::date
      ELSE EXISTS(SELECT 1 FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id
        WHERE x.revision_id=r.id AND d.category='CONSENT' AND profile->>'consentReference'='Client consent document / '||d.id::text
          AND (d.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date=(profile->>'consentOn')::date)
      END)
    ELSE (profile->>'retentionReviewOn')::date>=day AND coalesce(length(trim(profile->>'retentionReference')),0)>0
    END
  AND (SELECT count(DISTINCT d.category) FROM client_onboarding_document_link x JOIN client_onboarding_document d ON d.id=x.document_id WHERE x.revision_id=r.id AND (d.expires_on IS NULL OR d.expires_on>=day) AND d.category IN ('IDENTITY','ADDRESS','ACTIVITY','CONSENT') AND (NOT coalesce((r.profile->>'selfService')::boolean,false) OR d.category<>'CONSENT'))=CASE WHEN coalesce((r.profile->>'selfService')::boolean,false) THEN 3 ELSE 4 END
  AND (NOT coalesce((r.profile->>'selfService')::boolean,false) OR (
    coalesce((r.profile->>'noticeAcknowledged')::boolean,false) AND coalesce((r.profile->>'declarationAccepted')::boolean,false)
    AND (r.profile->>'acknowledgedAt')::timestamptz IS NOT NULL
    AND (profile->>'noticeId'='59bf1107-d9c1-44ca-a91f-e4b266123d3c' OR
      EXISTS(SELECT 1 FROM client_onboarding_notice n WHERE n.id=(r.profile->>'noticeId')::uuid
        AND n.sacco_id=r.sacco_id AND n.station_id=r.station_id AND n.enabled
        AND n.version=r.profile->>'noticeVersion' AND n.english=r.profile->>'noticeEnglish' AND n.kiswahili=r.profile->>'noticeKiswahili'
        AND n.lawful_basis=r.profile->>'lawfulBasis' AND n.retention_reference=r.profile->>'retentionReference'
        AND n.retention_review_on=(r.profile->>'retentionReviewOn')::date))))
  FROM client_onboarding_revision r WHERE id=revision),false)
$$;
