public class Transaction2 {
    int txnID;
    float amount;
    TransactionType2 type;
    float balance;
    String note;

    Transaction2(int txnID, float amount, TransactionType2 type, float balance, String note){
        this.txnID = txnID;
        this.amount = amount;
        this.type = type;
        this.balance = balance;
        this.note = note;
    }
}
