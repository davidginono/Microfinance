param(
    [ValidateRange(1024,65535)][int]$Port = 55439,
    [ValidateSet('microfinance_accounting_h_test','microfinance_accounting_h_release_test','microfinance_accounting_h_release_final_test','microfinance_accounting_h_release_combined_test_20261004','microfinance_accounting_h_release_integrity_test_20261004')]
    [string]$SourceDatabase = 'microfinance_accounting_h_test',
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\17\bin'
)

# Synthetic-only recovery rehearsal. It creates a new target and never deletes or overwrites a database.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskSourceDatabase = $SourceDatabase
$taskTargetDatabase = 'microfinance_h_restore_' + (Get-Date -Format 'yyyyMMddHHmmss')
$taskEvidenceDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('microfinance-h-recovery-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskEvidenceDirectory | Out-Null
$taskArchive = Join-Path $taskEvidenceDirectory 'synthetic-accounting.backup'
$taskPsql = Join-Path $PostgresBin 'psql.exe'
$taskDump = Join-Path $PostgresBin 'pg_dump.exe'
$taskRestore = Join-Path $PostgresBin 'pg_restore.exe'
$taskCreate = Join-Path $PostgresBin 'createdb.exe'
foreach ($taskTool in @($taskPsql,$taskDump,$taskRestore,$taskCreate)) {
    if (-not (Test-Path -LiteralPath $taskTool -PathType Leaf)) { throw "PostgreSQL tool unavailable: $taskTool" }
}

function Read-SyntheticQuery([string]$Database,[string]$Query) {
    $taskOutput = & $taskPsql -h 127.0.0.1 -p $Port -U microfinance_test -d $Database -v ON_ERROR_STOP=1 -A -t -c $Query
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic verification query failed.' }
    return ($taskOutput -join "`n")
}

$taskActive = Read-SyntheticQuery $taskSourceDatabase "SELECT count(*) FROM report_runs WHERE status IN('QUEUED','RUNNING')"
if ($taskActive.Trim() -ne '0') { throw 'Finish or cancel synthetic queued/running jobs before recovery comparison.' }
$taskSessions = Read-SyntheticQuery $taskSourceDatabase "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND state='active'"
if ($taskSessions.Trim() -ne '0') { throw 'Wait for active synthetic verification transactions before recovery comparison.' }
$taskArtifactCount = Read-SyntheticQuery $taskSourceDatabase 'SELECT count(*) FROM report_artifacts'
if ([long]$taskArtifactCount -lt 1) { throw 'Run the synthetic artifact tests before rehearsing recovery.' }

$taskSignatureQuery = @'
SELECT 'RUN|'||id||'|'||status||'|'||COALESCE(result_checksum,'')||'|'||COALESCE(approved_by::text,'')
 FROM report_runs
UNION ALL SELECT 'ARTIFACT|'||id||'|'||run_id||'|'||format||'|'||checksum||'|'||encode(sha256(payload),'hex')||'|'||octet_length(payload) FROM report_artifacts
UNION ALL SELECT 'PAGE|'||run_id||'|'||page||'|'||row_count||'|'||checksum||'|'||encode(sha256(convert_to(payload,'UTF8')),'hex')||'|'||octet_length(payload) FROM report_result_pages
UNION ALL SELECT 'LOGO|'||run_id||'|'||media_type||'|'||checksum||'|'||encode(sha256(payload),'hex')||'|'||octet_length(payload) FROM report_run_assets
UNION ALL SELECT 'MIGRATION|'||installed_rank||'|'||COALESCE(version,'')||'|'||COALESCE(checksum::text,'')||'|'||success FROM flyway_schema_history
ORDER BY 1
'@
$taskMismatchQuery = "SELECT (SELECT count(*) FROM report_artifacts WHERE checksum<>encode(sha256(payload),'hex')) + (SELECT count(*) FROM report_result_pages WHERE checksum<>encode(sha256(convert_to(payload,'UTF8')),'hex')) + (SELECT count(*) FROM report_run_assets WHERE checksum<>encode(sha256(payload),'hex'))"
# All names are fixed here; neither a database object name nor SQL is accepted from user input.
$taskRetainedTables = @(
    'accounting_policies','accounting_policy_approvals','gl_account','accounting_period',
    'gl_journal','gl_journal_line','gl_cutover_coverage','gl_operational_bridge','gl_source_cancellation','accounting_outbox',
    'reconciliation_format','reconciliation_statement','reconciliation_statement_line',
    'reconciliation_match','reconciliation_allocation','reconciliation_match_decision',
    'reconciliation_exception','reconciliation_exception_decision','reconciliation_certificate',
    'reconciliation_certificate_decision','accounting_close_review','accounting_close_decision',
    'financial_statement_templates','financial_statement_versions','financial_statement_results',
    'regulatory_statement_formats','regulatory_statement_submissions','regulatory_statement_reviews',
    'statement_output_sets','statement_output_artifacts','statement_output_reviews',
    'accounting_release_requests','accounting_release_decisions','accounting_release_invalidations',
    'loan_ledgers','loan_ledger_installments','loan_repayment_transactions',
    'loan_repayment_allocations','loan_journal_entries',
    'operational_report_templates','operational_report_template_versions',
    'accounting_business_document','accounting_business_control_entry','accounting_supplier','accounting_fixed_asset',
    'cash_flow_allocations','cash_flow_allocation_reviews',
    'accounting_release_branch_sources','accounting_release_cash_sources',
    'reconciliation_statement_file','reconciliation_certificate_statement','reconciliation_timing_source',
    'financial_statement_institution_sources','registered_saccos','sacco_stations'
)
$taskAvailableTables = @()
foreach ($taskTable in $taskRetainedTables) {
    if ((Read-SyntheticQuery $taskSourceDatabase "SELECT to_regclass('public.$taskTable') IS NOT NULL").Trim() -eq 't') {
        $taskAvailableTables += $taskTable
    }
}
$taskManifestParts = @($taskAvailableTables | ForEach-Object {
    "SELECT '$($_)|' || encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex') AS retained_row FROM $($_) t"
})
$taskManifestQuery = 'SELECT retained_row FROM (' + ($taskManifestParts -join ' UNION ALL ') + ') retained ORDER BY retained_row'
$taskSourceManifest = Read-SyntheticQuery $taskSourceDatabase $taskManifestQuery
$taskSourceManifest | Set-Content -LiteralPath (Join-Path $taskEvidenceDirectory 'source-retained-manifest.txt') -Encoding utf8
if ($taskAvailableTables -contains 'statement_output_sets') {
    $taskMismatchQuery += " + (SELECT count(*) FROM statement_output_sets WHERE result_checksum<>encode(sha256(convert_to(result_json,'UTF8')),'hex') OR (logo IS NOT NULL AND logo_checksum<>encode(sha256(logo),'hex'))) + (SELECT count(*) FROM statement_output_artifacts WHERE checksum<>encode(sha256(payload),'hex')) + (SELECT count(*) FROM accounting_release_requests WHERE dependency_checksum<>encode(sha256(convert_to(dependency_json,'UTF8')),'hex')) + (SELECT count(*) FROM financial_statement_results WHERE checksum<>encode(sha256(convert_to(result_json,'UTF8')),'hex')) + (SELECT count(*) FROM accounting_close_review WHERE checksum<>encode(sha256(convert_to(snapshot_json,'UTF8')),'hex'))"
}
if ((Read-SyntheticQuery $taskSourceDatabase $taskMismatchQuery).Trim() -ne '0') { throw 'Source payload checksum verification failed.' }
$taskSourceSignature = Read-SyntheticQuery $taskSourceDatabase $taskSignatureQuery
$taskSourceSignature | Set-Content -LiteralPath (Join-Path $taskEvidenceDirectory 'source-signature.txt') -Encoding utf8
if ((Read-SyntheticQuery 'postgres' "SELECT count(*) FROM pg_database WHERE datname='$taskTargetDatabase'").Trim() -ne '0') { throw 'Synthetic restore target already exists; it will not be overwritten.' }
$taskStarted = Get-Date
& $taskDump -h 127.0.0.1 -p $Port -U microfinance_test -d $taskSourceDatabase --format=custom --file=$taskArchive
if ($LASTEXITCODE -ne 0) { throw 'Synthetic backup failed.' }
& $taskCreate -h 127.0.0.1 -p $Port -U microfinance_test $taskTargetDatabase
if ($LASTEXITCODE -ne 0) { throw 'Creating the new synthetic restore target failed.' }
& $taskRestore -h 127.0.0.1 -p $Port -U microfinance_test -d $taskTargetDatabase --exit-on-error $taskArchive
if ($LASTEXITCODE -ne 0) { throw 'Synthetic restore failed.' }
if ((Read-SyntheticQuery $taskTargetDatabase $taskMismatchQuery).Trim() -ne '0') { throw 'Restored payload checksum verification failed.' }
$taskRestoredSignature = Read-SyntheticQuery $taskTargetDatabase $taskSignatureQuery
$taskRestoredSignature | Set-Content -LiteralPath (Join-Path $taskEvidenceDirectory 'restored-signature.txt') -Encoding utf8
if ($taskSourceSignature -cne $taskRestoredSignature) { throw 'Restored report versions, checksums, counts, or migration history differ.' }
$taskRestoredManifest = Read-SyntheticQuery $taskTargetDatabase $taskManifestQuery
$taskRestoredManifest | Set-Content -LiteralPath (Join-Path $taskEvidenceDirectory 'restored-retained-manifest.txt') -Encoding utf8
if ($taskSourceManifest -cne $taskRestoredManifest) { throw 'Restored financial entries, reconciliation evidence, statement versions, or release decisions differ.' }
$taskBalanceQuery = @'
SELECT count(*) FROM (
 SELECT j.id FROM gl_journal j LEFT JOIN gl_journal_line l ON l.journal_id=j.id
 WHERE j.state IN('APPROVED','POSTED') GROUP BY j.id
 HAVING count(l.id)<2 OR sum(l.debit)<>sum(l.credit)
) unbalanced
'@
if ((Read-SyntheticQuery $taskTargetDatabase $taskBalanceQuery).Trim() -ne '0') { throw 'Restored approved or posted journals do not balance.' }

$taskProtectionQuery = @'
DO $$
DECLARE protected boolean := false;
BEGIN
 BEGIN
  UPDATE report_artifacts SET payload='synthetic tamper'::bytea WHERE id=(SELECT id FROM report_artifacts LIMIT 1);
 EXCEPTION WHEN raise_exception THEN
  IF SQLERRM LIKE '%immutable%' THEN protected := true; ELSE RAISE; END IF;
 END;
 IF NOT protected THEN RAISE EXCEPTION 'Restored immutable artifact protection is missing'; END IF;
END $$;
'@
Read-SyntheticQuery $taskTargetDatabase $taskProtectionQuery | Out-Null
if ($taskAvailableTables -contains 'statement_output_artifacts') {
    $taskStatementProtection = $taskProtectionQuery.Replace('report_artifacts','statement_output_artifacts')
    Read-SyntheticQuery $taskTargetDatabase $taskStatementProtection | Out-Null
}
$taskRecord = [ordered]@{
    source = $taskSourceDatabase; restored = $taskTargetDatabase; loopbackPort = $Port
    backupSha256 = (Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    backupBytes = (Get-Item -LiteralPath $taskArchive).Length
    elapsedSeconds = [math]::Round(((Get-Date)-$taskStarted).TotalSeconds,3)
    signaturesMatch = $true; allPayloadChecksumsVerified = $true; restoredArtifactProtection = $true
    artifactCount = [long]$taskArtifactCount; evidenceDirectory = $taskEvidenceDirectory
    retainedTablesVerified = $taskAvailableTables; financialManifestMatches = $true
    restoredPostedJournalsBalanced = $true; monetaryCommandReplayVerified = $false
    operationalDataUsed = $false; approvedHumanRelease = $false
}
$taskRecord | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskEvidenceDirectory 'recovery-evidence.json') -Encoding utf8
$taskRecord | ConvertTo-Json
