package com.green.back;

import java.util.List;
import java.util.Map;

public interface DatabaseConnection {
    static final String SIGNUP_SUCCESS = "Account created successfully.";
    static final String SIGNUP_USERNAME_IN_USE = "Username already exists.";
    static final String SIGNUP_EMAIL_IN_USE = "Email already in use.";

    void deleteUser(String username);

    String signUp(String username, String password, String email);

    boolean login(String username, String password);

    String getFileName(String hash);

    Map<String, String> getFileRecordFromHash(String hash);

    void saveFile(String filename, String parent, String accnt);

    List<String[]> deleteFile(String filename);

    List<String> getRemoteFiles(String access_token);

    void saveAccount(String userId, String accessToken);

    List<Map<String, String>> getAccounts();

}
