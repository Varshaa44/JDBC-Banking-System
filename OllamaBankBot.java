import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaBankBot {
    private Bank bankEngine;

    public OllamaBankBot(Bank bankEngine) {
        this.bankEngine = bankEngine;
    }

    public void startChatSession(Customer loggedInCustomer) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n[BankBot]: Hello " + loggedInCustomer.name + "! I'm your AI banking assistant. I can help you with deposits and withdrawals.");
        System.out.println("Ask me: 'Deposit 2000 into Account 1' or 'Withdraw Rs 500 from Account 2'.");
        System.out.println("Type 'exit' to return to the main transaction menu.");

        while (true) {
            System.out.print("\nYou: ");
            String prompt = scanner.nextLine().trim();

            if (prompt.equalsIgnoreCase("exit")) {
                System.out.println("[BankBot]: Goodbye! Disconnecting from AI engine.");
                break;
            }

            // pass customer to queryOllama so it knows which accounts they own
            String aiResponse = queryOllama(prompt, loggedInCustomer);
            parseAndExecute(loggedInCustomer, aiResponse);
        }
    }

    private String loadInstructions() {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("chatbot_instructions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (java.io.IOException e) {
            return "Extract ACTION (deposit/withdraw), ACCOUNT number, and AMOUNT. Format: ACTION=x, ACCOUNT=y, AMOUNT=z";
        }
        return sb.toString();
    }

    // updated signature — now accepts customer to build account context
    private String queryOllama(String userPrompt, Customer customer) {
        try {
            java.net.URL url = java.net.URI.create("http://localhost:11434/api/generate").toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            String systemInstructions = loadInstructions();

            // build account context so model knows which accounts this customer owns
            StringBuilder accountContext = new StringBuilder();
            accountContext.append("STRICT RULE: The logged in customer is " + customer.name + ". ");
            accountContext.append("This customer ONLY owns these accounts: ");
            for(Account a : customer.accounts){
                accountContext.append("Account " + a.accNo + " (Balance: Rs " + a.balance + ") ");
                
            }
            accountContext.append("You must NEVER use any account number that is not in this list. ");
            accountContext.append("If the user says 'my history', 'show history', or does not specify an account, ");
            accountContext.append("always default to account " + customer.accounts.get(0).accNo + ". ");
            accountContext.append("Never use account 1001, 1002, 1003 or any account not listed above.");

            String jsonPayload = "{"
                + "\"model\": \"qwen2.5:3b\","
                + "\"prompt\": \"" + systemInstructions.replace("\n", "\\n").replace("\"", "\\\"")
                + "\\n\\nCustomer context: " + accountContext.toString().replace("\"", "\\\"")
                + "\\n\\nUser prompt: " + userPrompt + "\","
                + "\"stream\": false"
                + "}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            String fullJsonResponse = response.toString();
            int startIdx = fullJsonResponse.indexOf("\"response\":\"") + 12;
            if(startIdx > 11){
                int endIdx = startIdx;
                while(endIdx < fullJsonResponse.length()){
                    if(fullJsonResponse.charAt(endIdx) == '"'
                    && fullJsonResponse.charAt(endIdx-1) != '\\'){
                        break;
                    }
                    endIdx++;
                }
                return fullJsonResponse.substring(startIdx, endIdx)
                    .replace("\\n", " ")
                    .trim();
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private void parseAndExecute(Customer customer, String aiOutput) {
        try {
            String cleanOutput = aiOutput.trim();
            cleanOutput = cleanOutput.replaceAll("\\s*\\n\\s*", " ").trim();
            cleanOutput = cleanOutput.replaceAll("```json", "").replaceAll("```", "").trim();

            if (cleanOutput.contains("{")) {
                int jsonStartIndex = cleanOutput.indexOf("{");
                int jsonEndIndex = cleanOutput.lastIndexOf("}") + 1;

                String chatMessage = cleanOutput.substring(0, jsonStartIndex).trim();
                String jsonPart = cleanOutput.substring(jsonStartIndex, jsonEndIndex).trim();
                jsonPart = jsonPart.replace("\\\"", "\"");

                if (!chatMessage.isEmpty()) {
                    System.out.println("[BankBot]: " + chatMessage);
                }

                String action = extractJsonKeyValue(jsonPart, "action");

                int accountNo = 0;
                float amount = 0;

                if (!action.equalsIgnoreCase("details")) {
                    String accStr = extractJsonKeyValue(jsonPart, "account");
                    String amtStr = extractJsonKeyValue(jsonPart, "amount");
                    if (!accStr.isEmpty()) accountNo = Integer.parseInt(accStr);
                    if(accountNo > 0 && customer.getAccount(accountNo) == null){
                    System.out.println("[BankBot]: Account " + accountNo + " does not belong to you. Using your default account.");
                    accountNo = customer.accounts.get(0).accNo;
                    if (!amtStr.isEmpty()) amount = Float.parseFloat(amtStr);
                }

                if (action.equalsIgnoreCase("deposit") || action.equalsIgnoreCase("withdraw") || action.equalsIgnoreCase("transfer")) {
                    if (accountNo < 1001) {
                        System.out.println("[BankBot]: Stopped! Invalid values parsed. (Account must be 1001 or higher.)");
                        return;
                    }
                    if (amount <= 0) {
                        System.out.println("[BankBot]: Stopped! Invalid values parsed. (Amount must be positive.)");
                        return;
                    }
                }

                if (action.equalsIgnoreCase("deposit")) {
                    Account targetAccount = bankEngine.findAccount(accountNo);
                    if (targetAccount == null) return;
                    System.out.println("[BankBot]: Passing parameter maps to DepositHandler...");
                    DepositHandler.execute(targetAccount, amount, customer);

                } else if (action.equalsIgnoreCase("withdraw")) {
                    Account targetAccount = bankEngine.findAccount(accountNo);
                    if (targetAccount == null) return;
                    System.out.println("[BankBot]: Passing parameter maps to WithdrawHandler...");
                    WithdrawHandler.execute(targetAccount, amount, customer);

                } else if (action.equalsIgnoreCase("transfer")) {
                    int targetAccountNo = 0;
                    if (jsonPart.contains("targetAccount")) {
                        targetAccountNo = Integer.parseInt(extractJsonKeyValue(jsonPart, "targetAccount"));
                    }
                    Account sourceAccount = bankEngine.findAccount(accountNo);
                    Account destinationAccount = bankEngine.findAccount(targetAccountNo);
                    if (sourceAccount == null || destinationAccount == null) {
                        System.out.println("[BankBot]: Transfer aborted! One or both account numbers do not exist.");
                        return;
                    }
                    System.out.println("[BankBot]: Passing parameter maps to TransferHandler...");
                    TransferHandler.execute(sourceAccount, amount, destinationAccount, customer);

                } else if (action.equalsIgnoreCase("history")) {
                    Account targetAccount = bankEngine.findAccount(accountNo);
                    if (targetAccount == null) return;
                    System.out.println("[BankBot]: Passing parameter maps to HistoryHandler...");
                    HistoryHandler.execute(targetAccount);

                } else if (action.equalsIgnoreCase("details")) {
                    System.out.println("[BankBot]: Passing parameter maps to DetailsHandler...");
                    DetailsHandler.execute(customer);

                } else {
                    System.out.println("[BankBot]: Unrecognized bank action request.");
                }
            } else {
                System.out.println("[BankBot]: " + cleanOutput);
            }
        }

    } catch (Exception e) {
            System.out.println("[BankBot]: Failed parsing JSON string mapping format.");
            e.printStackTrace();
        }
    }

    private String extractJsonKeyValue(String json, String key) {
        String targetKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(targetKey);
        if (keyIndex == -1) return "";

        int colonIndex = json.indexOf(":", keyIndex);
        int endIndex = json.indexOf(",", colonIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf("}", colonIndex);
        }

        return json.substring(colonIndex + 1, endIndex)
                .replace("\"", "")
                .replace(" ", "")
                .trim();
    }
}