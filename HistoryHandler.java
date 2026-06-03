public class HistoryHandler {
    public static void execute(Account activeAcc) {
        if (activeAcc == null) {
            System.out.println("[HistorySystem]: Account profile not found.");
            return;
        }
        System.out.println("[HistorySystem]: Fetching transaction ledger from MySQL...");
        activeAcc.printHistory(); 
    }
}
