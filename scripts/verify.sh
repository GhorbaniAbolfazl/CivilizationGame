#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

# PHASE 3 - Added in Phase 3
gson_jar="${GSON_JAR:-${HOME}/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar}"
# PHASE 3 - Added in Phase 3
if [[ ! -f "$gson_jar" ]]; then
  echo "Gson 2.10.1 not found. Run Maven once or set GSON_JAR." >&2
  exit 1
fi

# PHASE 3 - Added in Phase 3
main_classpath="target/classes:$gson_jar"
# PHASE 3 - Added in Phase 3
test_classpath="target/classes:target/test-classes:$gson_jar"

rm -rf target/classes target/test-classes
mkdir -p target/classes target/test-classes

javac --release 17 -Xlint:all \
  -cp "$gson_jar" \
  -d target/classes \
  $(find src/main/java -name '*.java' -print)

javac --release 17 -Xlint:all \
  -cp "$main_classpath" \
  -d target/test-classes \
  $(find src/test/java -name '*.java' -print)

java -ea -cp "$test_classpath" civilization.model.CoreModelTestSuite
java -ea -cp "$test_classpath" civilization.application.MandatoryMechanicsTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.map.MapMovementTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.ui.SwingUiTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.bonus.BonusFeatureTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.Phase2CoreSystemsTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.HallTechnologyMilitaryTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.TradeSystemTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.CombatSystemTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.NeutralTribeSystemTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.MapInfrastructureEmpireTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.SeasonDisasterSystemTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.SaveLoadSystemTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.PerformanceAcceptanceTestSuite
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase2.PerformanceIntegrationReviewTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3PredefinedMapTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3ArchitectureFoundationTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3LobbyTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3TurnSynchronizationTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3StateBroadcastTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3ConnectionFailureTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3TextChatTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3PvpCombatTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3PlayerTradeTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3PlayerDiplomacyTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3MultipleHallTownTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3ApothecaryItemTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3SwingIntegrationTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3SecurityAuditTestSuite
# PHASE 3 - Added in Phase 3
java -ea -Djava.awt.headless=true \
  -cp "$test_classpath" civilization.phase3.Phase3ConcurrencyPerformanceAuditTestSuite
