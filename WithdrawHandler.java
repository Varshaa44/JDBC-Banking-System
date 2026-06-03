

public class WithdrawHandler {
    public static void execute(Account account, float amount, Customer customer) {
        if (amount <= 0) {
            System.out.println("[BankBot]: Withdrawal amount must be positive.");
            return;
        }
        customer.withdraw(account, amount);
    }
}
