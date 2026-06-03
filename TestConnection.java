import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestConnection {
    public static void main(String[] args) {
        // Connection string pointing to your local MySQL instance and your banksystem schema
        String url = "jdbc:mysql://localhost:3306/BANK";
        String user = "root"; 
        String password = "Varsha06"; // Change this to your actual MySQL root password

        try {
            // 1. Establish connection
            System.out.println("Connecting to MySQL Server...");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connection Successful!");

            // 2. Test a simple query from your customer table
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM bcustomer");

            System.out.println("\n--- Customers in DB ---");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("cus_id") + " | Name: " + rs.getString("name"));
            }

            // 3. Clean up resources
            rs.close();
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("Database connection error occurred!");
            e.printStackTrace();
        }
    }
}