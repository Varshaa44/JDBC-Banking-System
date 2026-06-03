import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DepositHandler {
    public static void execute(Account account, float amount, Customer customer) {
        if (amount <= 0) {
            System.out.println("[BankBot]: Deposit amount must be positive.");
            return;
        }        
        customer.deposit(account, amount);
    }
}
