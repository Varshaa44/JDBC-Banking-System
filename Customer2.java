import java.util.ArrayList;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class Customer2 {
    int cusID;
    String name;
    ArrayList<Account2> accounts = new ArrayList<>();
    PasswordManager2 pwdManager;
    int txnCount = 0;
    boolean mustChangePwd = false; // false = Transaction2s allowed, true = must change password before next txn

    Customer2(int cusID, String name, String password){
        this.cusID = cusID;
        this.name = name;
        Account2 firstAccount2 = new Account2(1000 + cusID);
        accounts.add(firstAccount2);
        this.pwdManager = new PasswordManager2();
        this.pwdManager.encryptedPwd = PasswordManager2.encrypt(password);
        // opening Transaction2
        firstAccount2.history.add(new Transaction2(Bank2.nextTxnID++, 10000, TransactionType2.OPENING, firstAccount2.balance, ""));
    }

    Account2 getAccount2(int accNo){
        for(Account2 a : accounts){
          if(a.accNo == accNo) return a;
        }
        return null;
    }

    boolean login(String password){
        if(pwdManager.verifyPassword(password)){
            System.out.println("Login successful. Welcome " + name + "!");
            return true;
        }
        System.out.println("Invalid credentials.");
        return false;
    }

    void deposit(Account2 acc,float amt){
        if(mustChangePwd){ System.out.println("Please change your password first."); return; }
        try {
            // Step 1: Update the balance in the account table
            String updateBalance = "UPDATE account SET balance = balance + ? WHERE acc_no = ?";
            PreparedStatement pstmtUpdate = Bank2.conn.prepareStatement(updateBalance);
            pstmtUpdate.setFloat(1, amt);
            pstmtUpdate.setInt(2, acc.accNo);
            pstmtUpdate.executeUpdate();

            // Step 2: Get the newly updated balance value for the transaction log
            String checkBalance = "SELECT balance FROM account WHERE acc_no = ?";
            PreparedStatement pstmtCheck = Bank2.conn.prepareStatement(checkBalance);
            pstmtCheck.setInt(1, acc.accNo);
            ResultSet rs = pstmtCheck.executeQuery();
            float currentBalance = 0;
            if(rs.next()) {
                currentBalance = rs.getFloat("balance");
            }

            // Step 3: Insert into transaction table
            String logTxn = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'DEPOSIT', ?, ?, '')";
            PreparedStatement pstmtLog = Bank2.conn.prepareStatement(logTxn);
            pstmtLog.setInt(1, acc.accNo);
            pstmtLog.setFloat(2, amt);
            pstmtLog.setFloat(3, currentBalance);
            pstmtLog.executeUpdate();

            txnCount++;
            
            // Step 4: Sync the updated txn_count back to customer table
            String updateTxnCnt = "UPDATE customer SET txn_count = ? WHERE cus_id = ?";
            PreparedStatement pstmtCount = Bank2.conn.prepareStatement(updateTxnCnt);
            pstmtCount.setInt(1, txnCount);
            pstmtCount.setInt(2, this.cusID);
            pstmtCount.executeUpdate();

            checkForcePasswordChange();
            System.out.println("Deposit of Rs " + amt + " processed and logged successfully.");

        } catch (SQLException e) {
            System.out.println("Database transaction failure.");
            e.printStackTrace();
        }
    }

    void withdraw(Account2 acc, float amt) {
        if (mustChangePwd) { System.out.println("Please change your password first."); return; }

        // Step 1: Check if the account has enough money first
        if (acc.balance < amt) {
            System.out.println("Insufficient balance! Current balance: Rs " + acc.balance);
            return;
        }

        try {
            // Step 2: Deduct money from the database balance column
            String updateBalance = "UPDATE account SET balance = balance - ? WHERE acc_no = ?";
            java.sql.PreparedStatement pstmtUpdate = Bank2.conn.prepareStatement(updateBalance);
            pstmtUpdate.setFloat(1, amt);
            pstmtUpdate.setInt(2, acc.accNo);
            pstmtUpdate.executeUpdate();

            // Step 3: Fetch the newly updated balance value for accuracy
            String checkBalance = "SELECT balance FROM account WHERE acc_no = ?";
            java.sql.PreparedStatement pstmtCheck = Bank2.conn.prepareStatement(checkBalance);
            pstmtCheck.setInt(1, acc.accNo);
            java.sql.ResultSet rs = pstmtCheck.executeQuery();
            float currentBalance = 0;
            if (rs.next()) {
                currentBalance = rs.getFloat("balance");
            }

            // Step 4: Write the WITHDRAWAL record straight to your transaction table
            String logTxn = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'WITHDRAWAL', ?, ?, 'Cash Withdrawal')";
            java.sql.PreparedStatement pstmtLog = Bank2.conn.prepareStatement(logTxn);
            pstmtLog.setInt(1, acc.accNo);
            pstmtLog.setFloat(2, amt);
            pstmtLog.setFloat(3, currentBalance);
            pstmtLog.executeUpdate();

            // Sync structural states
            acc.balance = currentBalance;
            txnCount++;
            
            // Sync transaction counter for the password rules
            String updateTxnCnt = "UPDATE customer SET txn_count = ? WHERE cus_id = ?";
            java.sql.PreparedStatement pstmtCount = Bank2.conn.prepareStatement(updateTxnCnt);
            pstmtCount.setInt(1, txnCount);
            pstmtCount.setInt(2, this.cusID);
            pstmtCount.executeUpdate();

            checkForcePasswordChange();
            System.out.println("Withdrawal of Rs " + amt + " processed and logged successfully!");

        } catch (java.sql.SQLException e) {
            System.out.println("Database execution failure during withdrawal handling.");
            e.printStackTrace();
        }
    }

    public void transfer(Account2 source, float amt, Account2 target) {
        if (mustChangePwd) { System.out.println("Please change your password first."); return; }
        
        // 1. Check if source has enough money
        if (source.balance < amt) {
            System.out.println("Insufficient funds for transfer! Current balance: Rs " + source.balance);
            return;
        }

        try {
            // Turn off Auto-Commit to ensure all updates succeed or fail together safely
            Bank2.conn.setAutoCommit(false);

            // ---- STEP 1: DEBIT FROM SOURCE ----
            String debitQuery = "UPDATE account SET balance = balance - ? WHERE acc_no = ?";
            try (PreparedStatement pstmtDebit = Bank2.conn.prepareStatement(debitQuery)) {
                pstmtDebit.setFloat(1, amt);
                pstmtDebit.setInt(2, source.accNo);
                pstmtDebit.executeUpdate();
            }

            // ---- STEP 2: CREDIT TO TARGET ----
            String creditQuery = "UPDATE account SET balance = balance + ? WHERE acc_no = ?";
            try (PreparedStatement pstmtCredit = Bank2.conn.prepareStatement(creditQuery)) {
                pstmtCredit.setFloat(1, amt);
                pstmtCredit.setInt(2, target.accNo);
                pstmtCredit.executeUpdate();
            }

            // ---- STEP 3: FETCH NEW BALANCES FOR AUDITING LOGS ----
            float sourceNewBal = 0, targetNewBal = 0;
            String checkBal = "SELECT acc_no, balance FROM account WHERE acc_no IN (?, ?)";
            try (PreparedStatement pstmtCheck = Bank2.conn.prepareStatement(checkBal)) {
                pstmtCheck.setInt(1, source.accNo);
                pstmtCheck.setInt(2, target.accNo);
                try (ResultSet rs = pstmtCheck.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getInt("acc_no") == source.accNo) sourceNewBal = rs.getFloat("balance");
                        if (rs.getInt("acc_no") == target.accNo) targetNewBal = rs.getFloat("balance");
                    }
                }
            }

            // ---- STEP 4: LOG THE SENDER'S TRANSACTION (TRANSFER_OUT) ----
            String logOut = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'TRANSFER_OUT', ?, ?, ?)";
            try (PreparedStatement pstmtLogOut = Bank2.conn.prepareStatement(logOut)) {
                pstmtLogOut.setInt(1, source.accNo);
                pstmtLogOut.setFloat(2, amt);
                pstmtLogOut.setFloat(3, sourceNewBal);
                pstmtLogOut.setString(4, "Transferred to Acc " + target.accNo);
                pstmtLogOut.executeUpdate();
            }

            // ---- STEP 5: LOG THE RECEIVER'S TRANSACTION (TRANSFER_IN) ----
            String logIn = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'TRANSFER_IN', ?, ?, ?)";
            try (PreparedStatement pstmtLogIn = Bank2.conn.prepareStatement(logIn)) {
                pstmtLogIn.setInt(1, target.accNo);
                pstmtLogIn.setFloat(2, amt);
                pstmtLogIn.setFloat(3, targetNewBal);
                pstmtLogIn.setString(4, "Received from Acc " + source.accNo);
                pstmtLogIn.executeUpdate();
            }

            // Update local object variables tracking states
            source.balance = sourceNewBal;
            txnCount++;

            // ---- STEP 6: UPDATE CUSTOMER TRANSACTION COUNTER ----
            String updateTxnCnt = "UPDATE customer SET txn_count = ? WHERE cus_id = ?";
            try (PreparedStatement pstmtCount = Bank2.conn.prepareStatement(updateTxnCnt)) {
                pstmtCount.setInt(1, txnCount);
                pstmtCount.setInt(2, this.cusID);
                pstmtCount.executeUpdate();
            }

            // Commit the entire pipeline successfully to your drive
            Bank2.conn.commit();
            System.out.println("Transfer of Rs " + amt + " to Account " + target.accNo + " processed successfully!");
            
            checkForcePasswordChange();

        } catch (SQLException e) {
            try {
                Bank2.conn.rollback(); // Undo everything if any query fails mid-execution
                System.out.println("Transfer failed! System changes rolled back safely.");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            try { Bank2.conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    void checkForcePasswordChange(){
        if(txnCount > 0 && txnCount % 3 == 0){
            System.out.println("Security alert: You must change your password before next transaction.");
            mustChangePwd = true;
            try {
                String query = "UPDATE customer SET must_change_pwd = TRUE WHERE cus_id = ?";
                PreparedStatement pstmt = Bank2.conn.prepareStatement(query);
                pstmt.setInt(1, this.cusID);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    void checkMaintenanceFee(Account2 acc, ArrayList<Customer2> topN){
        try {
            // 1. Query the database to count the total transactions for this specific account
            String countQuery = "SELECT COUNT(*) FROM transaction WHERE acc_no = ?";
            int totalTxns = 0;
            try (PreparedStatement pstmtCount = Bank2.conn.prepareStatement(countQuery)) {
                pstmtCount.setInt(1, acc.accNo);
                try (ResultSet rs = pstmtCount.executeQuery()) {
                    if (rs.next()) {
                        totalTxns = rs.getInt(1);
                    }
                }
            }

            // 2. Trigger the maintenance fee check if transaction volume exceeds 10 entries
            if (totalTxns > 10) {
                boolean isTop = false;
                if (topN != null) {
                    for (Customer2 c : topN) {
                        if (c.cusID == this.cusID) { isTop = true; break; }
                    }
                }

                // If they are not a top VIP customer, deduct the Rs 100 maintenance fee
                if (!isTop) {
                    // Update database balance column
                    String updateBal = "UPDATE account SET balance = balance - 100 WHERE acc_no = ?";
                    try (PreparedStatement pstmtUpdate = Bank2.conn.prepareStatement(updateBal)) {
                        pstmtUpdate.setInt(1, acc.accNo);
                        pstmtUpdate.executeUpdate();
                    }

                    // Get the fresh balance total for the logging snapshot
                    String getBal = "SELECT balance FROM account WHERE acc_no = ?";
                    float currentBalance = 0;
                    try (PreparedStatement pstmtBal = Bank2.conn.prepareStatement(getBal)) {
                        pstmtBal.setInt(1, acc.accNo);
                        try (ResultSet rs = pstmtBal.executeQuery()) {
                            if (rs.next()) currentBalance = rs.getFloat("balance");
                        }
                    }

                    // Log the official transaction ledger entry row
                    String logFee = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'MAINTENANCE_FEE', 100, ?, 'Monthly Account Maintenance')";
                    try (PreparedStatement pstmtLog = Bank2.conn.prepareStatement(logFee)) {
                        pstmtLog.setInt(1, acc.accNo);
                        pstmtLog.setFloat(2, currentBalance);
                        pstmtLog.executeUpdate();
                    }

                    // Update your local structural state tracking reference
                    acc.balance = currentBalance;
                    System.out.println("Maintenance fee of Rs 100 automatically deducted and logged.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error processing maintenance fee checks.");
            e.printStackTrace();
        }
    }

    void printDetails(){
        System.out.println("Customer ID : " + this.cusID);
        System.out.println("Name        : " + this.name);
        
        String query = "SELECT acc_no, balance FROM account WHERE cus_id = ?";
        try (java.sql.PreparedStatement pstmt = Bank2.conn.prepareStatement(query)) {
            pstmt.setInt(1, this.cusID);
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                int accNo = rs.getInt("acc_no");
                float bal = rs.getFloat("balance");
                System.out.println("Account No  : " + accNo + "  Balance: Rs " + bal);
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error pulling details from database.");
            e.printStackTrace();
        }
    }
}
