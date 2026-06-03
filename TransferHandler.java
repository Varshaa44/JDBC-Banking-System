public class TransferHandler {
    public static void execute(Account sourceAcc, float amount, Account targetAcc, Customer customer) {
        if (sourceAcc == null || targetAcc == null) {
            System.out.println("[TransferSystem]: Error - Missing account profiles.");
            return;
        }
        System.out.println("[TransferSystem]: Authorizing funds transfer exchange...");
        customer.transfer(sourceAcc, amount, targetAcc); //calls the transfer method in cutomer class
    }
}
