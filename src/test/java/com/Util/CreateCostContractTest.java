package com.Util;

import static com.Util.SourceContract.before;
import static com.Util.SourceContract.read;
import static com.Util.SourceContract.region;
import static com.Util.SourceContract.requireContains;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the payment contract at the real creation charge call sites.
 *
 * <p>These are source-text checks because the code they guard needs a live Paper server,
 * Vault and PlayerPoints to execute. They go through {@link SourceContract} so a rename
 * reports the missing marker instead of throwing an index exception.</p>
 */
class CreateCostContractTest {

   @Test
   @DisplayName("money charges are recorded only after Vault reports success")
   void moneyChargeChecksProviderResult() {
      String source = read("com", "ErrorTown", "CommandListener.java");
      String window = before(source, "the money charge call site", "CreateCostLedger.recordMoney", 1200);
      requireContains(window, "transactionSuccess()", "money charges must verify EconomyResponse.transactionSuccess() before recording");
      requireContains(window, "Variable.econ.withdrawPlayer", "the recorded amount must be the one actually withdrawn");
   }

   @Test
   @DisplayName("point charges are recorded only after PlayerPoints reports success")
   void pointChargeChecksProviderResult() {
      String source = read("com", "ErrorTown", "CommandListener.java");
      String window = before(source, "the points charge call site", "CreateCostLedger.recordPoints", 1200);
      requireContains(window, "if (!Variable.playerPoints.getAPI().take", "points charges must verify PlayerPoints.take() before recording");
   }

   @Test
   @DisplayName("a player with an unsettled creation charge cannot be charged again")
   void outstandingChargeBlocksAnotherCreateCostFlow() {
      String source = read("com", "ErrorTown", "CommandListener.java");
      String method = region(source, "canStartCreate", "private boolean canStartCreate", "\n   private ");
      requireContains(method, "CreateCostLedger.hasCharge",
         "canStartCreate must reject a second paid attempt while the first charge is unsettled");
   }

   @Test
   @DisplayName("custom-seed reset settles a successful charge and refunds a failed world creation")
   void customSeedResetClosesItsLedgerEntry() {
      String source = read("com", "Listeners", "PlayerChatListener.java");
      String branch = region(source, "the custom-seed reset branch", "inputType.startsWith(\"seed:\")", "inputType.startsWith(\"create_seed:\")");
      requireContains(branch, "CreateCostLedger.settle", "custom-seed reset must close its charge on success");
      requireContains(branch, "CreateCostLedger.refund", "custom-seed reset must refund on failure");
      requireContains(branch, "totalCost > 0 && Variable.econ == null",
         "custom-seed reset must stop when a positive price is configured but Vault is unavailable");
   }

   @Test
   @DisplayName("creation callbacks use the coordinator request token, not a home-name lookup")
   void creationCallbacksCarryExactRequestIdentity() {
      String coordinator = read("com", "Util", "HomeCreationCoordinator.java");
      String command = read("com", "ErrorTown", "CommandListener.java");

      requireContains(coordinator, "ThreadLocal<HomeCreationQueue.CreationRequest>",
         "the coordinator must carry the dispatched request token into the synchronous command flow");
      requireContains(coordinator, "currentRequest(homeName)", "the coordinator must expose the token by home name");
      requireContains(command, "consumeAdmission(HomeCreationCoordinator.currentRequest(createHomeName))",
         "command callbacks must consume the exact dispatched request");
   }
}
