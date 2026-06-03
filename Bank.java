import java.util.ArrayList;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class Bank {
    static Connection conn;
    static int nextTxnID = 1;
    ArrayList<Customer> Customers = new ArrayList<>();
    int nextCusID = 1;
    int nextAccNo = 1001;
    Scanner sc = new Scanner(System.in);

    void connectToDatabase() {
        String url = "jdbc:mysql://localhost:3306/JDBC_BANK_SYSTEM";
        String user = null;
        String password = null;

        // Load credentials dynamically from the local properties config file
        java.io.File configFile = new java.io.File("db.properties");
        if (configFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                java.util.Properties props = new java.util.Properties();
                props.load(fis);
                user = props.getProperty("db.user");
                password = props.getProperty("db.password");
            } catch (java.io.IOException e) {
                System.out.println("⚠️ Found db.properties but failed to read it.");
            }
        }

        // Fallback: If local file isn't found, try looking at system environments
        if (user == null || password == null) {
            user = System.getenv("DB_USER");
            password = System.getenv("DB_PASSWORD");
        }

        // Ultimate Security Guardrail Check
        if (user == null || password == null) {
            System.out.println("⚡Error: Database credentials missing! Create a 'db.properties' file locally.");
            return;
        }

        try {
            Bank.conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to BANK database successfully.");
        } catch (SQLException e) {
            System.out.println("Database connection failed! Verify your MySQL Server is running.");
            e.printStackTrace();
        }
    }

    void addCustomer(){
        System.out.print("Enter name: ");
        String name = sc.nextLine();
        
        String password = "";
        java.io.Console console = System.console();
        
        if (console != null) {
            System.out.print("Enter password (typing will be hidden): ");
            char[] passwordChars = console.readPassword();
            password = new String(passwordChars);
        } else {
            System.out.print("Enter password: ");
            password = sc.nextLine();
        }

        System.out.print("Do you want to view the password to double-check? (y/n): ");
        String choice = sc.nextLine().trim().toLowerCase();
        if (choice.equals("y") || choice.equals("yes")) {
            System.out.println("~ Your password is: " + password);
            System.out.print("Proceed with this password? (y/n): ");
            String confirm = sc.nextLine().trim().toLowerCase();
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Registration failed. Try again.");
                return;
            }
        }

        if(!PasswordManager.pwdComplexity(password)){
            System.out.println("Password does not meet complexity requirements.");
            System.out.println("Need: 6+ chars, 2 uppercase, 2 lowercase, 2 digits.");
            return;
        }
        String encryptedPassword = PasswordManager.encrypt(password);

        try {
            // 1. Insert customer details
            String insertCustomer = "INSERT INTO customer (name, encrypted_pwd) VALUES (?, ?)";
            PreparedStatement pstmtCust = conn.prepareStatement(insertCustomer, Statement.RETURN_GENERATED_KEYS);
            pstmtCust.setString(1, name);
            pstmtCust.setString(2, encryptedPassword);
            pstmtCust.executeUpdate();
            
            ResultSet rsCust = pstmtCust.getGeneratedKeys();
            int newCusId = 0;
            if (rsCust.next()) {
                newCusId = rsCust.getInt(1);
            }

            // 2. Open default account (Will auto-increment acc_no starting at 1001)
            String insertAccount = "INSERT INTO account (cus_id) VALUES (?)";
            PreparedStatement pstmtAcc = conn.prepareStatement(insertAccount, Statement.RETURN_GENERATED_KEYS);
            pstmtAcc.setInt(1, newCusId);
            pstmtAcc.executeUpdate();
            
            ResultSet rsAcc = pstmtAcc.getGeneratedKeys();
            int newAccNo = 0;
            if (rsAcc.next()) {
                newAccNo = rsAcc.getInt(1);
            }

            // 3. Log initial opening transaction
            String insertTxn = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'OPENING', 10000, 10000, 'Initial Account Opening')";
            PreparedStatement pstmtTxn = conn.prepareStatement(insertTxn);
            pstmtTxn.setInt(1, newAccNo);
            pstmtTxn.executeUpdate();

            System.out.println("Customer created! ID: " + newCusId + "  Account No: " + newAccNo);

        } catch (SQLException e) {
            System.out.println("Database error during customer registration.");
            e.printStackTrace();
        }
    }
    

    void addAccountToCustomer(int cusID){
        try {
            // Step 1: Insert a new account into the database linked to this cusID
            String insertAccount = "INSERT INTO account (cus_id) VALUES (?)";
            PreparedStatement pstmtAcc = conn.prepareStatement(insertAccount, Statement.RETURN_GENERATED_KEYS);
            pstmtAcc.setInt(1, cusID);
            pstmtAcc.executeUpdate();
            
            // Step 2: Grab the newly generated account number (auto-incremented)
            ResultSet rsAcc = pstmtAcc.getGeneratedKeys();
            int newAccNo = 0;
            if (rsAcc.next()) {
                newAccNo = rsAcc.getInt(1);
            }

            // Step 3: Log the mandatory opening transaction record for this new account
            String insertTxn = "INSERT INTO transaction (acc_no, type, amt, balance_after, note) VALUES (?, 'OPENING', 10000, 10000, 'Additional Account Opening')";
            PreparedStatement pstmtTxn = conn.prepareStatement(insertTxn);
            pstmtTxn.setInt(1, newAccNo);
            pstmtTxn.executeUpdate();

            System.out.println("New account created successfully! Account No: " + newAccNo);

        } catch (SQLException e) {
            System.out.println("Database error while adding an additional account.");
            e.printStackTrace();
        }
    }

    // Customer findCustomer(int cusID){
    //     for(Customer c : Customers){
    //         if(c.cusID == cusID) return c;
    //     }
    //     System.out.println("Customer not found.");
    //     return null;
    // } 

    ArrayList<Customer> topN(int n){
        ArrayList<Customer> sorted = new ArrayList<>(Customers);
        sorted.sort((a, b) -> {
            float maxA=0, maxB=0;
            for(Account acc : a.accounts) if(acc.balance > maxA) maxA = acc.balance;
            for(Account acc : b.accounts) if(acc.balance > maxB) maxB = acc.balance;
            return Float.compare(maxB, maxA);
        });
        int limit = Math.min(n, sorted.size());
        return new ArrayList<>(sorted.subList(0, limit));
    }

    Account findAccount(int accNo){
        String query = "SELECT acc_no, balance FROM account WHERE acc_no = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, accNo);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                // Return a temporary structural wrapper holding the core parameters
                Account a = new Account(rs.getInt("acc_no"));
                a.balance = rs.getFloat("balance");
                return a;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("Account not found in system database.");
        return null;
    }

    Account selectAccount(Customer c){
        ArrayList<Account> dbAccounts = new ArrayList<>();
        
        // 1. Fetch all accounts belonging to this customer from the database
        String query = "SELECT acc_no, balance FROM account WHERE cus_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, c.cusID);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Account a = new Account(rs.getInt("acc_no"));
                a.balance = rs.getFloat("balance");
                dbAccounts.add(a);
            }
        } catch (SQLException e) {
            System.out.println("Error fetching user accounts for selection.");
            e.printStackTrace();
            return null;
        }

        // 2. If they have no accounts at all
        if (dbAccounts.isEmpty()) {
            System.out.println("You don't have any active accounts.");
            return null;
        }

        // 3. If they only have 1 account, select it automatically without asking
        if (dbAccounts.size() == 1) {
            return dbAccounts.get(0);
        }
        
        // 4. If they have multiple accounts, display them and let them choose
        System.out.println("Your Accounts:");
        for (Account a : dbAccounts) {
            System.out.println(" " + a.accNo + " - Rs " + a.balance);
        }
        
        System.out.print("Select Account no: ");
        int selectedNo = Integer.parseInt(sc.nextLine());
        
        // Find and return the matching account from our temporary database list
        for (Account a : dbAccounts) {
            if (a.accNo == selectedNo) {
                return a;
            }
        }
        
        System.out.println("Invalid Account selection.");
        return null;
    }

    void printTopN(){
        System.out.print("Enter N: ");
        int n = Integer.parseInt(sc.nextLine());
        ArrayList<Customer> top = topN(n);
        System.out.println("Top " + n + " Customers:");
        System.out.println("ID\tName\tAccount\tBalance");
        System.out.println("--------------------------------------------");
        for(Customer c : top){
            for(Account a:c.accounts){
                System.out.println(c.cusID + "\t" + c.name + "\t" + a.accNo + "\t\tRs" + a.balance);
            }
        }
    }

    Customer loginMenu(){
        System.out.print("Enter Customer ID: ");
        int id = Integer.parseInt(sc.nextLine());
        System.out.print("Enter Password: ");
        String pwd = sc.nextLine();
        
        String encryptedInput = PasswordManager.encrypt(pwd);
        String query = "SELECT * FROM customer WHERE cus_id = ?";
        
        try {
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String dbPassword = rs.getString("encrypted_pwd");
                String customerName = rs.getString("name");
                
                if (dbPassword.equals(encryptedInput)) {
                    System.out.println("Login successful. Welcome " + customerName + "!");
                    
                    // Log to your login_log table
                    PreparedStatement logPstmt = conn.prepareStatement("INSERT INTO login_log (cus_id, status) VALUES (?, 'SUCCESS')");
                    logPstmt.setInt(1, id);
                    logPstmt.executeUpdate();
                    
                    Customer c = new Customer(id, customerName, pwd);
                    c.mustChangePwd = rs.getBoolean("must_change_pwd");
                    c.txnCount = rs.getInt("txn_count");
                    return c;
                }
            }
            
            System.out.println("Invalid credentials.");
            // Log the failure if customer exists, or skip if completely invalid ID
            return null;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    void TransactionMenu(Customer c){
        while(true){
            System.out.println("\n-- Transactions --");
            System.out.println("1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Transfer");
            System.out.println("4. View history");
            System.out.println("5. View details");
            System.out.println("6. Change password");
            System.out.println("7. Add new Account");
            System.out.println("8. Talk to AI Chatbot!");
            System.out.println("9. Logout");
            System.out.print("Choice: ");
            String choice = sc.nextLine();

            if(choice.equals("1")){
                System.out.print("Enter Account Number to deposit into: ");
                int accNo = Integer.parseInt(sc.nextLine());
                Account activeAcc = findAccount(accNo); // Fetches your account details from DB
    
                if (activeAcc != null) {
                    System.out.print("Amount: ");
                    float amount = Float.parseFloat(sc.nextLine());
                    c.deposit(activeAcc, amount); // Runs your JDBC deposit method cleanly!
                }
            } else if (choice.equals("2")) {
                System.out.print("Enter Account Number to withdraw from: ");
                int accNo = Integer.parseInt(sc.nextLine());
                Account activeAcc = findAccount(accNo); // Fetches live database balance snapshot
    
                if (activeAcc != null) {
                    System.out.print("Amount: ");
                    float amount = Float.parseFloat(sc.nextLine());
                    c.withdraw(activeAcc, amount); // Runs our brand new DB method!
                }
            } else if(choice.equals("3")){
                Account acc = selectAccount(c);
                if(acc == null) continue;
                System.out.print("Target Account No: ");
                int targetAccNo = Integer.parseInt(sc.nextLine());
                Account target = findAccount(targetAccNo);
                if(target == null){
                    System.out.println("Account not found."); 
                    continue; 
                }
                System.out.print("Amount: ");
                float amt = Float.parseFloat(sc.nextLine());
                c.transfer(acc, amt, target);

            } else if (choice.equals("4")) {
                System.out.print("Enter Account Number to view history: ");
                int accNo = Integer.parseInt(sc.nextLine());
                Account activeAcc = findAccount(accNo);
    
                if (activeAcc != null) {
                    activeAcc.printHistory(); // Executes the query from our previous step!
                }
            } else if(choice.equals("5")){
                c.printDetails();

            } else if(choice.equals("6")){
                System.out.print("Old password: ");
                String old = sc.nextLine();
                System.out.print("New password: ");
                String newP = sc.nextLine();
                System.out.print("Confirm new password: ");
                String confirm = sc.nextLine();
                if(c.pwdManager.changePassword(c.cusID,old, newP, confirm)){
                    c.mustChangePwd = false;
                }

            }else if(choice.equals("7")){
                addAccountToCustomer(c.cusID);
                System.out.println("New Account added - " + c.cusID);

            } else if(choice.equals("8")){
                OllamaBankBot bot = new OllamaBankBot(this); //calls ollamabot constructor
                bot.startChatSession(c);

            } else if(choice.equals("9")){
                System.out.println("Logged out.");
                break;  
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    void mainMenu(){
        while(true){
            System.out.println("\n====== Bank Menu ======");
            System.out.println("1. New Customer");
            System.out.println("2. Login");
            System.out.println("3. Top N Customers");
            System.out.println("4. Exit");
            System.out.print("Choice: ");
            String choice = sc.nextLine();

            if(choice.equals("1")){
                addCustomer();
            } else if(choice.equals("2")){
                Customer c = loginMenu();
                if(c != null) TransactionMenu(c);
            } else if(choice.equals("3")){
                printTopN();
            } else if(choice.equals("4")){
                System.out.println("Goodbye!");
                break;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    public static void main(String[] args){
        Bank Bank = new Bank(); // ollama run qwen3.5:4b2 in cmd prompt before executing this main method 
        // //currently using qwen2.5:3b cuz my laptops slow ;-;
        Bank.connectToDatabase();
        Bank.mainMenu();
    }
}

//ps: this is a starter project and command-line output of "tables" might be clumsy, rest is good :D
