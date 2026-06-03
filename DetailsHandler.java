public class DetailsHandler {
    public static void execute(Customer customer) {
        System.out.println("[DetailsSystem]: Displaying profile credentials...");
        customer.printDetails(); 
    }
}
