package com.green.back;


import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostgresDatabaseConnection implements DatabaseConnection{
    private static final String DB_USERNAME = "postgres";
    private static final String DB_PWD = "@Potinas235_pos";
    private static final String DB_NAME = "InfiniteDrop";
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "5432";

    public String username = "";
    public String user_id = "";

    private static PostgresDatabaseConnection dbConnection = null;

    public static PostgresDatabaseConnection getDBConnection() {
        if (PostgresDatabaseConnection.dbConnection == null) {
            PostgresDatabaseConnection.dbConnection = new PostgresDatabaseConnection();
        }

        return PostgresDatabaseConnection.dbConnection;
    }

    private void executeStatement(String sql) {
        Connection c = null;
        Statement stmt = null;
        try {
            c = DriverManager
                    .getConnection(String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME),
                            DB_USERNAME, DB_PWD);
            stmt = c.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName()+": "+e.getMessage());
            System.exit(0);
        }

    }

    private List<Map<String, String>> executeQuery(String sql) {
        Connection c = null;
        Statement stmt = null;
        ResultSet rs = null;
        List<Map<String, String>> results = new ArrayList<>();
        try {
            c = DriverManager
                    .getConnection(String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME),
                            DB_USERNAME, DB_PWD);
            stmt = c.createStatement();
            rs = stmt.executeQuery(sql);
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();
            while(rs.next()) {
                Map<String, String> row = new HashMap<>();
                for (int i=1;i<=columns;i++){
                    row.put(md.getColumnName(i), rs.getString(i));
                }
                results.add(row);
            }
            stmt.close();
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName()+": "+e.getMessage());
            System.exit(0);
        }

        return results;

    }

    @Override
    public String signUp(String username, String password, String email) {
        String username_sql = String.format("SELECT count(*) as c FROM infinite_drop_user WHERE username='%s'", username);
        String email_sql = String.format("SELECT count(*) as c FROM infinite_drop_user WHERE email='%s'", email);
        String user_sql = String.format("INSERT INTO infinite_drop_user (username, password, email) VALUES ('%s', '%s', '%s')",
                                        username, CombinedFileManager.getHash(password), email);

        List<Map<String, String>> rs = executeQuery(username_sql);
        if(Integer.parseInt(rs.get(0).get("c")) > 0){
            return DatabaseConnection.SIGNUP_USERNAME_IN_USE; // TODO: Switch to Enums.
        }
        rs = executeQuery(email_sql);
        if(Integer.parseInt(rs.get(0).get("c")) > 0){
            return DatabaseConnection.SIGNUP_EMAIL_IN_USE;
        }

        executeStatement(user_sql);

        return DatabaseConnection.SIGNUP_SUCCESS;
    }

    @Override
    public void deleteUser(String username) {
        String user_sql = String.format("DELETE FROM infinite_drop_user WHERE username='%s'", username);
        executeStatement(user_sql);
    }

    @Override
    public boolean login(String username, String password) {
        String login_sql = String.format("SELECT id FROM infinite_drop_user WHERE username='%s' AND password='%s'",
                                            username, CombinedFileManager.getHash(password));
        List<Map<String, String>> rs = executeQuery(login_sql);
        if( !rs.isEmpty() && rs.get(0).containsKey("id")){
            this.username = username;
            this.user_id = rs.get(0).get("id");
            return true;
        }
        System.out.println(login_sql);
        return false;
    }

    @Override
    public String getFileName(String hash) {
        String sql = String.format("SELECT filename FROM file WHERE hash='%s'", hash);
        List<Map<String, String>> rs = executeQuery(sql);
        if( !rs.isEmpty() && rs.get(0).containsKey("filename") ) {
            return rs.get(0).get("filename");
        }
        return null;
    }

    @Override
    public Map<String, String> getFileRecordFromHash(String hash) {
        String sql = String.format("SELECT * FROM file WHERE hash='%s'", hash);
        List<Map<String, String>> rs = executeQuery(sql);
        Map<String, String> fileRecord = new HashMap<>();
        if (!rs.isEmpty()) {
            fileRecord = rs.get(0);
        }
        return fileRecord;
    }

    public List<String> getRemoteFiles(String access_token) {
        String sql = String.format("SELECT hash FROM file WHERE infinite_drop_user_id in (select infinite_drop_user_id from dbx_account where dbx_access_token='%s')", access_token);
        List<Map<String, String>> rs = executeQuery(sql);
        List<String> files = new ArrayList<>();
        for (Map<String, String> row: rs) {
            files.add("/"+row.get("hash"));
        }

        return files;
    }

    @Override
    public void saveFile(String filename, String parent, String accnt_token) {
        String hash = CombinedFileManager.getHash(filename);
        String sql = String.format("INSERT INTO file (infinite_drop_user_id, hash, filename, parent_file_hash, dbx_account_id) VALUES ('%s', '%s', '%s', '%s', (select id from dbx_account where dbx_access_token='%s'))",
                                    this.user_id, hash, filename, parent, accnt_token);
        executeStatement(sql);

    }

    @Override
    public List<String[]> deleteFile(String hash) {
        String select_children_sql = String.format("SELECT filename, dbx_access_token  FROM file,dbx_account WHERE file.dbx_account_id=dbx_account.id AND file.infinite_drop_user_id='%s' AND parent_file_hash='%s'",
                this.user_id, hash);
        String select_file_sql = String.format("SELECT filename, dbx_access_token  FROM file,dbx_account WHERE file.dbx_account_id=dbx_account.id AND file.infinite_drop_user_id='%s' AND hash='%s'",
                this.user_id, hash);
        String delete_children_sql = String.format("DELETE FROM file WHERE infinite_drop_user_id='%s' AND parent_file_hash='%s'",
                this.user_id, hash);
        String delete_file_sql = String.format("DELETE FROM file WHERE infinite_drop_user_id='%s' AND hash='%s'",
                this.user_id, hash);

        List<String[]> affectedFiles = new ArrayList<>();

        List<Map<String, String>> rs = executeQuery(select_children_sql);
        for (Map<String,String> row: rs) {
            affectedFiles.add(new String[]{row.get("filename"), row.get("dbx_access_token")});
        }

        rs = executeQuery(select_file_sql);
        for (Map<String,String> row: rs) {
            affectedFiles.add(new String[]{row.get("filename"), row.get("dbx_access_token")});
        }

        executeStatement(delete_children_sql);
        executeStatement(delete_file_sql);

        return affectedFiles;
    }

    @Override
    public void saveAccount(String userId, String accessToken) {
        String sql = String.format("INSERT INTO dbx_account (infinite_drop_user_id, dbx_user_id, dbx_access_token) VALUES ('%s', '%s', '%s') ON CONFLICT (infinite_drop_user_id, dbx_user_id) DO UPDATE SET dbx_access_token='%s'",
                this.user_id, userId, accessToken, accessToken, userId);
        executeStatement(sql);

    }

    @Override
    public List<Map<String, String>> getAccounts() {
        String sql = String.format("SELECT * FROM dbx_account WHERE infinite_drop_user_id='%s'", this.user_id);
        return executeQuery(sql);
    }

}
