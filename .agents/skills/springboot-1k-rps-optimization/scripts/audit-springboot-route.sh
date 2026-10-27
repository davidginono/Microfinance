#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
if [[ ! -d "$ROOT" ]]; then
  echo "Project root does not exist: $ROOT" >&2
  exit 2
fi

if command -v rg >/dev/null 2>&1; then
  SEARCH=(rg -n --hidden --glob '!target/**' --glob '!build/**' --glob '!.git/**')
else
  echo "ripgrep (rg) is recommended; falling back to grep." >&2
  SEARCH=(grep -RIn --exclude-dir=target --exclude-dir=build --exclude-dir=.git)
fi

scan() {
  local title="$1"
  local pattern="$2"
  echo
  echo "=== $title ==="
  "${SEARCH[@]}" -e "$pattern" "$ROOT" 2>/dev/null || true
}

echo "Heuristic Spring Boot route audit for: $ROOT"
echo "Findings require review; absence of a match is not proof of safety."

scan "Controller classes" '@(RestController|Controller)'
scan "Repository use (review any matches inside controllers)" '(JpaRepository|CrudRepository|PagingAndSortingRepository|Repository<|EntityManager)'
scan "Unbounded findAll calls" '\.findAll\s*\(\s*\)'
scan "Possible SELECT star" 'SELECT[[:space:]]+\*'
scan "Flush inside application code" '(saveAndFlush|\.flush\s*\()'
scan "Transaction annotations (review controller placement and scope)" '@Transactional'
scan "Async methods (must name a bounded executor)" '@Async'
scan "Raw thread/executor creation" '(new[[:space:]]+Thread|newCachedThreadPool|newFixedThreadPool|supplyAsync\s*\(|parallelStream\s*\()'
scan "Potential open-in-view enablement" 'spring\.jpa\.open-in-view[=:][[:space:]]*true'
scan "Unsafe production schema mutation" 'ddl-auto[=:][[:space:]]*(create|create-drop|update)'
scan "Field injection candidates" '@Autowired[[:space:]]*$'
scan "Possible raw JSP EL output" '\$\{[^}]+\}'
scan "Potential raw JSP escaping disablement" 'escapeXml[[:space:]]*=[[:space:]]*"false"'
scan "Potential hard-coded secrets" '(password|secret|api[_-]?key|private[_-]?key)[[:space:]]*[=:][[:space:]]*[^$<{]'

echo
echo "Suggested next steps:"
echo "1. Trace each hot route from controller to SQL."
echo "2. Verify controller -> service -> repository dependency direction."
echo "3. Run PostgreSQL integration/security tests."
echo "4. Capture query counts and EXPLAIN plans."
echo "5. Run a reproducible load test before making RPS claims."
