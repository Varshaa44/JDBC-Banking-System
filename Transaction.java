public class Transaction {
    int txnID;
    float amount;
    TransactionType type;
    float balance;
    String note;

    Transaction(int txnID, float amount, TransactionType type, float balance, String note){
        this.txnID = txnID;
        this.amount = amount;
        this.type = type;
        this.balance = balance;
        this.note = note;
    }
}
