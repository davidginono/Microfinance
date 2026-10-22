# Unified posting-account type

The 2026-10-22 user instruction removes Control account as a separate account type. New accounts are either groups (`HEADING`) or posting accounts (`POSTING`). Principal, receivable, allowance and charge accounts use Posting account too. Their purpose and workflow ownership determine permitted financial operations.

Existing immutable `CONTROL` identifiers remain a storage compatibility code for old accounts, submitted proposals and financial evidence. They display and filter as Posting account, and are eligible for product mappings. No account IDs, balances, codes, historical approval snapshots, journal lines or approval decisions are rewritten. Existing pending proposals retain their original evidence; the current approval workflow still governs new account proposals.

V88 introduces the shared `gl_account_requires_loan_source` predicate. Reserved loan purposes, generated product accounts, charge-policy/contract accounts and legacy restrictions remain protected from ordinary receipt/payment/journal and manual-template bypass. Specialised loan, charge, provision and independent correction validators retain their scope, amounts, dates, workflow and source checks, while accepting new posting accounts. The indexes bound account-ownership lookups by institution and account ID.

This instruction supersedes older documentation requiring a separate control-account classification. It does not authorize changing posted balances, bypassing loan allocation, granting claims or rewriting historical financial evidence.
