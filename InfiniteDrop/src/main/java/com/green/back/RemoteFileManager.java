package com.green.back;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class RemoteFileManager implements Runnable {
	private DatabaseConnection databaseConnection;
	private DropboxConnection dropboxConnection;
	private HashMap<String, String> cursors;
	private ArrayBlockingQueue<Metadata> eventQueue;
	
	private static final String SAVE_FILE = LocalFileManager.BASE_DIR + ".cursors";

	public RemoteFileManager() {
		cursors = new HashMap<>();
		databaseConnection = PostgresDatabaseConnection.getDBConnection();
		dropboxConnection = new DropboxConnection();
		eventQueue = new ArrayBlockingQueue<>(10000);
		
		readState();
	}

	public void run() {
		for (;;) {
			List<Map.Entry<String, DbxClientV2>> clients = dropboxConnection.getDbxClients();
			for (Map.Entry<String, DbxClientV2> clientEntry : clients) {
                List<String> remoteFiles = databaseConnection.getRemoteFiles(clientEntry.getKey());

                for (String file : remoteFiles) {
                    try {
                        ListFolderResult result = null;
                        if (cursors.containsKey(clientEntry.getKey()+file) && cursors.get(clientEntry.getKey()+file) != null) {
                            result = clientEntry.getValue().files().listFolderContinue(cursors.get(clientEntry.getKey() + file));
                        }else{
                            result = clientEntry.getValue().files().listFolder(file);
                        }

                        cursors.put(clientEntry.getKey()+file, result.getCursor());
                        eventQueue.addAll(result.getEntries());
                    } catch (DbxException e) {
                        e.printStackTrace();
                    }
                }

			}

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public Metadata takeDbxEvent() throws InterruptedException {
		return eventQueue.take();
	}
	
	public void readState() {
		List<Map.Entry<String, DbxClientV2>> clients = dropboxConnection.getDbxClients();
		HashMap<String, String> tempCursors = new HashMap<String, String>();
		File saveFile = new File(RemoteFileManager.SAVE_FILE);
		BufferedReader br;
		try {
			saveFile.createNewFile();
			br = new BufferedReader(new FileReader(saveFile));
			String line;
			while ((line = br.readLine()) != null) {
			   String left = line.substring(0, line.indexOf(":"));
			   String right = line.substring(line.indexOf(":") + 1);
			   tempCursors.put(left, right);
			}
			
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(Map.Entry<String, DbxClientV2> clientEntry: clients) {
			if(tempCursors.containsKey(clientEntry.getKey())) {
				cursors.put(clientEntry.getKey(), tempCursors.get(clientEntry.getKey()));
			}
		}
		
	}
	
	public void saveState() {
		File saveFile = new File(RemoteFileManager.SAVE_FILE);
		try {
			FileWriter f = new FileWriter(saveFile);
			for(Map.Entry<String, String> e: cursors.entrySet()) {
				f.write(e.getKey() + ":" + e.getValue() + "\n");
			}
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void saveFile(Path path) {
		File inputFile = path.toFile();
		DbxClientV2 client = dropboxConnection.getDbxClient(getLargestAccount());
		String hash = CombinedFileManager.getHash(LocalFileManager
				.getRelativePath(path.toString()));
		FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(inputFile);
			FileMetadata uploadedFile = client.files().uploadBuilder("/" + hash).uploadAndFinish(inputStream);
			System.out.println("Uploaded: " + uploadedFile.toString());
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void saveFolder(Path path) {
		DbxClientV2 client = dropboxConnection.getDbxClient(getLargestAccount());
		String hash = CombinedFileManager.getHash(LocalFileManager
				.getRelativePath(path.toString()));
		try {
			client.files().createFolderV2("/" + hash);
			System.out.println("Created Folder");
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void deleteFile(String path, String clientToken) {
		DbxClientV2 client = dropboxConnection.getDbxClient(clientToken);
		try {
			client.files().deleteV2("/" + path);
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public FileMetadata downloadFile(String hash) {
		Map<String, String> record = databaseConnection.getFileRecordFromHash(hash);
		String localName = record.get("file");
		DbxClientV2 client = dropboxConnection.getDbxClient(record.get("dbxAccount"));
		FileOutputStream outputStream;

		try {
			outputStream = new FileOutputStream(LocalFileManager.BASE_DIR + "/" + localName);
		    FileMetadata downloadedFile = client.files().download("/magnum-opus.txt").download(outputStream);
		    System.out.println("Metadata: " + downloadedFile.toString());
		    outputStream.close();
		    return downloadedFile;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DbxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public String getLargestAccount() {
		long max = Long.MIN_VALUE;
		String dbxAccnt = null;
		for (Map.Entry<String, DbxClientV2> clientEntry : dropboxConnection.getDbxClients()) {
			long curr = Long.MIN_VALUE;
			try {
				curr = clientEntry.getValue().users().getSpaceUsage().getAllocation().getIndividualValue().getAllocated()
						- clientEntry.getValue().users().getSpaceUsage().getUsed();
			} catch (DbxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (curr > max) {
				max = curr;
				dbxAccnt = clientEntry.getKey();
			}
		}
		return dbxAccnt;
	}
}
