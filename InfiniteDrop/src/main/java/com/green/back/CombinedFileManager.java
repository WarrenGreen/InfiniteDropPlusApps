package com.green.back;

import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;

import javax.xml.bind.DatatypeConverter;

public class CombinedFileManager implements Runnable {
	private LocalFileManager localFileManager;
	private RemoteFileManager remoteFileManager;
	private DatabaseConnection databaseConnection;
	private DropboxConnection dropboxConnection;

	private static ConcurrentHashMap<String, Integer> eventLog = new ConcurrentHashMap<String, Integer>();
	
	public CombinedFileManager() {
		databaseConnection = PostgresDatabaseConnection.getDBConnection();
        dropboxConnection = new DropboxConnection();
		localFileManager = new LocalFileManager();
		remoteFileManager = new RemoteFileManager();
		Thread localManager = new Thread(localFileManager, "localFileManager");
		Thread remoteManager = new Thread(remoteFileManager, "remoteFileManager");
		remoteManager.start();
		localManager.start();
	}

	@Override
	public void run() {
		Thread localWatch = new Thread() {
			public void run() {
				SimpleEntry<Path, WatchEvent<Path>> ev = null;
				for(;;) {
					try {
						 ev = localFileManager.takeWatchEvent();
						 handleLocalEvent(ev);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		};
		
		Thread remoteWatch = new Thread() {
			public void run() {
				Metadata ev = null;
				for(;;) {
					try {
						 ev = remoteFileManager.takeDbxEvent();
						 handleRemoteEvent(ev);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		};
		
		localWatch.start();
		remoteWatch.start();

	}
	
	private void handleLocalEvent(SimpleEntry<Path, WatchEvent<Path>> e) {
		WatchEvent<Path> ev = e.getValue();
		String hash = getHash(localFileManager.getRelativePath(e.getKey().toString()));
		//if(checkEventRepeat(hash)) {
		//	return;
		//}
		
		if( ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
			incrementLog(hash);
			String accnt = remoteFileManager.getLargestAccount();
			String path = localFileManager.getRelativePath(e.getKey().toString());
			String parent = localFileManager.getRelativePath(e.getKey().getParent().toString());
			if( e.getKey().toFile().isDirectory()) {
				remoteFileManager.saveFolder(e.getKey());
			} else {
				remoteFileManager.saveFile(e.getKey());
			}
			databaseConnection.saveFile(path, getHash(parent), accnt);
			System.out.println("Created file: " + ev.context());
		} else if (ev.kind() == StandardWatchEventKinds.ENTRY_DELETE ) {
			incrementLog(hash);
			List<String[]> files = databaseConnection.deleteFile(hash);
			for (String[] file: files) {

                remoteFileManager.deleteFile(CombinedFileManager.getHash(file[0]), file[1]);
            }
			System.out.println("Deleted file: " + ev.context());
		} else if (ev.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
			System.out.println("Changed file: " + ev.context());
		}
	}
	
	private void handleRemoteEvent(Metadata entry) {
		String hash = entry.getPathLower().substring(1).toUpperCase();
		if(checkEventRepeat(hash)) {
			return;
		}
		System.out.println("Received Dbx Event: " + entry.getPathLower());
		if( entry instanceof DeletedMetadata && hash != null) {
			incrementLog(hash);
			String localfilename = databaseConnection.getFileName(hash);
			if (localfilename != null) {
                localFileManager.deleteFile(localfilename);
                databaseConnection.deleteFile(hash);
                System.out.println("Deleted Local File");
            }
		} else if(entry instanceof FolderMetadata){
			incrementLog(hash);
			String localFile = databaseConnection.getFileName(hash);
			localFileManager.createFolder(LocalFileManager.getFullPath(localFile));
			System.out.println("Created folder");
		} else {
			incrementLog(hash);
			remoteFileManager.downloadFile(hash);
			System.out.println("Downloaded File");
		}
	}
	
	private void incrementLog(String hash) {
		if(CombinedFileManager.eventLog.containsKey(hash)) {
			int val = CombinedFileManager.eventLog.get(hash) + 1;
			CombinedFileManager.eventLog.put(hash, val);
		} else {
			CombinedFileManager.eventLog.put(hash, 1);
		}
	}
	
	private boolean checkEventRepeat(String hash) {
		if(CombinedFileManager.eventLog.containsKey(hash)) {
			int val = CombinedFileManager.eventLog.get(hash) - 1;
			if(val == 0){
				CombinedFileManager.eventLog.remove(hash);
			}else {
				CombinedFileManager.eventLog.put(hash, val);
			}
			
			return true;
		}else{
			return false;
		}
	}

	public boolean login(String username, String password) {
	    boolean success = databaseConnection.login(username, CombinedFileManager.getHash(password));
	    if (success) {
            dropboxConnection.loginDbx(databaseConnection.getAccounts());
        }

        return success;
    }
	
	public static String getHash(String input) {
		String hash = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(input.getBytes("UTF-8"));
			hash = DatatypeConverter.printHexBinary(md.digest());

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return hash;
	}
	
	public void saveState() {
		remoteFileManager.saveState();
	}
	
}
