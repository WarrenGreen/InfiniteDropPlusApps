package com.green.back;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DropboxConnection {
    //Dropbox API Keys
    private static final String APP_KEY = "4ler1x1mc1aw2h7";
    private static final String APP_SECRET = "pjo9362exbzudi3";

    private final DbxRequestConfig config = new DbxRequestConfig(
            "InfiniteDrop/1.0", Locale.getDefault().toString());
    private final DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
    private DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);
    private static ConcurrentHashMap<String, DbxClientV2> clients = new ConcurrentHashMap<String, DbxClientV2>();

    public Map<String, DbxClientV2> loginDbx(List<Map<String, String>> accounts) {

        Map<String, DbxClientV2> clients = new HashMap<>();

        for (Map<String, String> account : accounts) {
            DbxClientV2 client = new DbxClientV2(config, account.get("dbxAccessToken"));
            clients.put(account.get("dbxAccessToken"), client); //TODO update db
        }

        return clients;
    }

    /**
     * Dropbox authorization
     */
    public String startAuth() {
        return webAuth.start();
    }

    public String[] finishAuth(String code) {
        try {
            DbxAuthFinish authFinish = webAuth.finish(code);
            String accessToken = authFinish.getAccessToken();
            DbxClientV2 client = new DbxClientV2(config, accessToken);
            clients.put(accessToken, client);
            return new String[]{authFinish.getUserId(), accessToken};
        } catch (DbxException e) {
            e.printStackTrace(); //TODO: save account
        }
        return null;
    }


    public List<Map.Entry<String, DbxClientV2>> getDbxClients(){
        List<Map.Entry<String, DbxClientV2>> ret = new ArrayList<>();
        ret.addAll(DropboxConnection.clients.entrySet());
        return ret;
    }

    public DbxClientV2 getDbxClient(String key){
        return DropboxConnection.clients.get(key);
    }
}
