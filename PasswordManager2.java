class PasswordManager2 {
    String encryptedPwd;
    String[] pwdHistory = new String[3];

    public static String encrypt(String password){
        if(password == null) return null;
        StringBuilder result = new StringBuilder();
        for(char c : password.toCharArray()){
            if(c >= 'a' && c <= 'z'){
                result.append((char)(((c - 'a' + 1) % 26) + 'a'));
            } else if(c >= 'A' && c <= 'Z'){
                result.append((char)(((c - 'A' + 1) % 26) + 'A'));
            } else if(c >= '0' && c <= '9'){
                result.append((char)(((c - '0' + 1) % 10) + '0'));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public boolean verifyPassword(String password){
        return encrypt(password).equals(encryptedPwd);
    }

    public static boolean pwdComplexity(String password){
        int upper = 0, lower = 0, digit = 0;
        for(char c : password.toCharArray()){
            if(Character.isUpperCase(c)) upper++;
            else if(Character.isLowerCase(c)) lower++;
            else if(Character.isDigit(c)) digit++;
        }
        return password.length() >= 6 && upper >= 2 && lower >= 2 && digit >= 2;
    }

    public boolean pwdReused(String newPassword){
        String encNew = encrypt(newPassword);
        for(String old : pwdHistory){
            if(old != null && old.equals(encNew)) return true;
        }
        return false;
    }

   public boolean changePassword(int cusId, String oldPwd, String newPassword, String confirmPassword) {
        if (!verifyPassword(oldPwd)) {
            System.out.println("Old password is incorrect.");
            return false;
        }
        if (!newPassword.equals(confirmPassword)) {
            System.out.println("Passwords do not match.");
            return false;
        }
        if (!pwdComplexity(newPassword)) {
            System.out.println("Password does not meet complexity requirements.");
            return false;
        }

        String encNew = encrypt(newPassword);

        try {
            // 1. Check if the password was recently used (Java logic matching pwdReused)
            String historyCheck = "SELECT COUNT(*) FROM pwdhistory WHERE cus_id = ? AND encrypt_pwd = ?";
            try (java.sql.PreparedStatement pstmtCheck = Bank2.conn.prepareStatement(historyCheck)) {
                pstmtCheck.setInt(1, cusId);
                pstmtCheck.setString(2, encNew);
                java.sql.ResultSet rs = pstmtCheck.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("Cannot reuse an old password!");
                    return false;
                }
            }

            // 2. Archive the current active password into the history table before overwriting it
            String archiveQuery = "INSERT INTO pwdhistory (cus_id, encrypt_pwd, slot) VALUES (?, ?, 1)";
            try (java.sql.PreparedStatement pstmtArchive = Bank2.conn.prepareStatement(archiveQuery)) {
                pstmtArchive.setInt(1, cusId);
                pstmtArchive.setString(2, this.encryptedPwd); // Saves current hash to history
                pstmtArchive.executeUpdate();
            }

            // 3. Keep only the last 3 password changes (Cleans up older rows so it doesn't grow forever)
            String cleanupQuery = "DELETE FROM pwdhistory WHERE cus_id = ? AND id NOT IN " +
                                  "(SELECT id FROM (SELECT id FROM pwdhistory WHERE cus_id = ? ORDER BY id DESC LIMIT 3) AS tmp)";
            try (java.sql.PreparedStatement pstmtClean = Bank2.conn.prepareStatement(cleanupQuery)) {
                pstmtClean.setInt(1, cusId);
                pstmtClean.setInt(2, cusId);
                pstmtClean.executeUpdate();
            }

            // 4. Update the master customer table with the new password hash
            String updateMaster = "UPDATE customer SET encrypted_pwd = ?, must_change_pwd = FALSE WHERE cus_id = ?";
            try (java.sql.PreparedStatement pstmtMaster = Bank2.conn.prepareStatement(updateMaster)) {
                pstmtMaster.setString(1, encNew);
                pstmtMaster.setInt(2, cusId);
                pstmtMaster.executeUpdate();
            }

            // Update local object variable tracking state
            this.encryptedPwd = encNew;
            System.out.println("Password changed successfully and recorded in history.");
            return true;

        } catch (java.sql.SQLException e) {
            System.out.println("Database error tracking password change adjustment.");
            e.printStackTrace();
            return false;
        }
    }
}
