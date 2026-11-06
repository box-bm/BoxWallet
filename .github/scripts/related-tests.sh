#!/usr/bin/env bash
# Resolves which unit tests are related to the changes between BASE and HEAD.
#
# Usage: related-tests.sh <base-sha> <head-sha>
# Outputs (stdout as key=value, also appended to $GITHUB_OUTPUT when set):
#   mode    = all | filtered | compile | none
#   filters = space-separated list of `--tests <FQCN>` gradle args
set -euo pipefail

BASE="${1:?base sha required}"
HEAD="${2:?head sha required}"
TEST_ROOT="app/src/test/java"
MAIN_ROOTS=("app/src/main/java" "app/src/main/kotlin")

mapfile -t CHANGED < <(git diff --name-only "$BASE...$HEAD")

mode="none"
declare -A TESTS=()

# path/to/com/foo/BarTest.kt -> com.foo.BarTest
fqcn() { local p="${1#"$TEST_ROOT"/}"; p="${p%.kt}"; echo "${p//\//.}"; }

add_test_file() {
  local f="$1"
  [[ -f "$f" ]] && grep -q "@Test" "$f" && TESTS["$(fqcn "$f")"]=1
  return 0
}

for f in "${CHANGED[@]}"; do
  case "$f" in
    # Build / dependency changes can affect anything -> full suite
    *.gradle.kts|*.gradle|gradle/*|gradle.properties|settings.gradle.kts|app/proguard-rules.pro)
      mode="all" ;;
    "$TEST_ROOT"/*.kt)
      [[ "$mode" == "all" ]] || mode="filtered"
      add_test_file "$f" ;;
    app/src/main/*.kt)
      [[ "$mode" == "all" ]] || { [[ "$mode" == "filtered" ]] || mode="compile"; }
      cls="$(basename "$f" .kt)"
      # 1) Convention: Foo.kt -> FooTest.kt
      while IFS= read -r t; do add_test_file "$t"; done \
        < <(find "$TEST_ROOT" -name "${cls}Test.kt" 2>/dev/null)
      # 2) Any test that references the class/file symbol
      while IFS= read -r t; do add_test_file "$t"; done \
        < <(grep -rlw --include='*.kt' "$cls" "$TEST_ROOT" 2>/dev/null || true)
      ;;
  esac
done

if [[ "$mode" == "compile" && ${#TESTS[@]} -gt 0 ]]; then mode="filtered"; fi
# e.g. only deleted tests -> nothing to filter on, but still verify compilation
if [[ "$mode" == "filtered" && ${#TESTS[@]} -eq 0 ]]; then mode="compile"; fi

filters=""
for t in "${!TESTS[@]}"; do filters+="--tests $t "; done
filters="${filters% }"

{
  echo "mode=$mode"
  echo "filters=$filters"
} | tee -a "${GITHUB_OUTPUT:-/dev/null}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Related tests"
    echo "**Mode:** \`$mode\`"
    if [[ ${#TESTS[@]} -gt 0 ]]; then
      echo; for t in "${!TESTS[@]}"; do echo "- \`$t\`"; done
    fi
  } >> "$GITHUB_STEP_SUMMARY"
fi
