import java.util.ArrayList;

class Account2 {
    int accNo;
    float balance = 10000;
    ArrayList<Transaction2> history = new ArrayList<>();

    Account2(int accNo){
        this.accNo = accNo;
    }

    synchronized void deposit(float amt){
        balance += amt;
        history.add(new Transaction2(Bank2.nextTxnID++, amt, TransactionType2.DEPOSIT, balance, ""));
    }

    synchronized boolean withdraw(float amt){
        if(balance - amt < 1000){
            System.out.println("Minimum balance of 1000 must be maintained.");
            return false;
        }
        balance -= amt;
        history.add(new Transaction2(Bank2.nextTxnID++, amt, TransactionType2.WITHDRAWAL, balance, ""));
        return true;
    }

    synchronized boolean transfer(float amt, Account2 target){
        if(balance - amt < 1000){
            System.out.println("Minimum balance of 1000 must be maintained.");
            return false;
        }
        balance -= amt;
        target.balance += amt;
        history.add(new Transaction2(Bank2.nextTxnID++, amt, TransactionType2.TRANSFER_OUT, balance, "to acc "+ target.accNo));
        target.history.add(new Transaction2(Bank2.nextTxnID++, amt, TransactionType2.TRANSFER_IN, target.balance, "from acc "+ accNo));
        if(amt > 5000){
            balance -= 10;
            history.add(new Transaction2(Bank2.nextTxnID++, 10, TransactionType2.OPERATION_FEE, balance,""));
        }
        return true;
    }

    void printHistory(){
        System.out.println("TxnID\tType\t\tDescription\tAmount\tBalance");
        System.out.println("-------------------------------------------------------");
        
        String query = "SELECT txn_id, type, amt, balance_after, note FROM transaction WHERE acc_no = ? ORDER BY txn_id ASC";
        try (java.sql.PreparedStatement pstmt = Bank2.conn.prepareStatement(query)) {
            pstmt.setInt(1, this.accNo);
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            while(rs.next()){
                int id = rs.getInt("txn_id");
                String type = rs.getString("type");
                float amount = rs.getFloat("amt");
                float postBal = rs.getFloat("balance_after");
                String note = rs.getString("note");
                
                // Keep spacing matching your classic layout terminal format
                System.out.println(id + "\t" + type + "\t\t" + note + "\t\t" + amount + "\t" + postBal);
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error loading historical statement ledger.");
            e.printStackTrace();
        }
    }
}