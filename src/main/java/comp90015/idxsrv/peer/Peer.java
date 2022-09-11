package comp90015.idxsrv.peer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingDeque;

import java.nio.charset.StandardCharsets;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.filemgr.BlockUnavailableException;
import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;

import comp90015.idxsrv.message.AuthenticateReply;
import comp90015.idxsrv.message.AuthenticateRequest;
import comp90015.idxsrv.message.DropShareReply;
import comp90015.idxsrv.message.DropShareRequest;
import comp90015.idxsrv.message.ErrorMsg;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.LookupReply;
import comp90015.idxsrv.message.LookupRequest;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.message.SearchReply;
import comp90015.idxsrv.message.SearchRequest;
import comp90015.idxsrv.message.ShareReply;
import comp90015.idxsrv.message.ShareRequest;
import comp90015.idxsrv.message.WelcomeMsg;
import comp90015.idxsrv.message.BlockRequest;
import comp90015.idxsrv.message.BlockReply;
import comp90015.idxsrv.message.Goodbye;
import comp90015.idxsrv.server.IndexMgr.RETCODE;
/**
 * Skeleton Peer class to be completed for Project 1.
 * @author aaron
 *
 */
public class Peer implements IPeer {

	private IOThread ioThread;
	
	private LinkedBlockingDeque<Socket> incomingConnections;
	
	private ISharerGUI tgui;
	
	private String basedir;
	
	private int timeout;
	
	private int port;
	
	public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
		this.tgui=tgui;
		this.port=port;
		this.timeout=socketTimeout;
		this.basedir=new File(basedir).getCanonicalPath();
		ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
		ioThread.start();

	}
	
	public void shutdown() throws InterruptedException, IOException {
		ioThread.shutdown();
		ioThread.interrupt();
		ioThread.join();
	}
	
	/*
	 * Students are to implement the interface below.
	 */

	/**
	 * Share the given file with the index server, using the provided secrets.
	 * An error is logged if the provided file is not within the basedir
	 * of the peer, in which case the request is ignored. The shared file
	 * should be added to the gui's shared file table, if the server request
	 * succeeds, using the {@link ISharerGUI} interface.
	 **/	
	@Override
	public void shareFileWithIdxServer(File file, InetAddress idxAddress, int idxPort, String idxSecret,
			String shareSecret) {
				
		//implement share file function here, All Peers must follow the same protocol to share file data, in the form of blocks. 
		//if file.getName() is a null(爲空), cancat will throw a NullPointerExeception; if it's a null string（空字符串, then this operation would be really fast
		/** Five ways of string concatenation in Java:
		 * '+', easy but slow
		 * StringBuffer.append()
		 * String.concat()
		 * String.join()
		 * StringJoiner
		 **/
		/**
	 	* Create a file descriptor for a given file, using a given block length.
	 	* */
		//FileDescr fileDescr = new FileDescr();
		//Socket socket = this.incomingConnections.take();
		Socket socket;
		try {
			socket = new Socket(idxAddress.getHostName(), idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
			//first need to send out authenticate request?
			writeMsg(bufferedWriter, new AuthenticateRequest(idxSecret));
			Message authMsg;
			try {
				authMsg = readMsg(bufferedReader);
			} catch (JsonSerializationException e1) {
				writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
				return;
			}
			if(authMsg.getClass().getName()==WelcomeMsg.class.getName()){
				tgui.logInfo("Received Welcome");
				try {
					authMsg = readMsg(bufferedReader);
				} catch (JsonSerializationException e1) {
					writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
					return;
				}
			}
			if(authMsg.getClass().getName()==AuthenticateReply.class.getName()){
				AuthenticateReply authReply = (AuthenticateReply) authMsg;
				if(!authReply.success){
					tgui.logError("authenticate failed!");
					return;
				} else {
					tgui.logInfo("authenticate succeed!");
					//send the shareRequest
					String filepath = this.basedir.concat('/' + file.getName());
					tgui.logInfo(file.getName());
					File f = new File(filepath);
					if(!f.exists()){
						tgui.logError("File doesn't exist!");
						writeMsg(bufferedWriter, new ErrorMsg("share failed"));
						//ignore the request
						return;
					} else{
						FileMgr fileMgr;
						try {
							fileMgr = new FileMgr(file.getName());
							tgui.logInfo("Sending a ShareRequest to Server.....");
							writeMsg(bufferedWriter,new ShareRequest(fileMgr.getFileDescr(), file.getName(), shareSecret, this.port));

							Message msg;
							try {
								msg = readMsg(bufferedReader);
							} catch (JsonSerializationException e1) {
								writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
								return;
							}
							String msgname = msg.getClass().getName();
							if(msgname==ShareReply.class.getName()) {
								ShareReply shareReply = (ShareReply) msg;
								tgui.addShareRecord(file.getName(), new ShareRecord(fileMgr, shareReply.numSharers, "success", idxAddress, idxPort, idxSecret, shareSecret));
								tgui.logInfo("One file successfully added!");
							}
						} catch (NoSuchAlgorithmException | IOException e) {
							// TODO Auto-generated catch block
							tgui.logError("Invalid message");
							return;
						}
					}	
				}	
			}
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			tgui.logError("!!!!!!!!!!!!!!!!!!");
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			tgui.logError("@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
			e1.printStackTrace();
		}
		
		//tgui.logError("shareFileWithIdxServer unimplemented");
	}

	/**
	 * Search the index server for filenames that contain all the keywords. Obtain
	 * at most `maxhits` results. The results must be added to the gui search table using
	 * the {@link ISharerGUI} interface.
	 * @param keywords
	 * @param maxhits
	 * @param idxAddress
	 * @param idxPort
	 * @param idxSecret
	 */
	@Override
	public void searchIdxServer(String[] keywords, 
			int maxhits, 
			InetAddress idxAddress, 
			int idxPort, 
			String idxSecret) {

		Socket socket;
		try {
			socket = new Socket(idxAddress.getHostName(), idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
			//no use of IdxSecret?? -----> first need to send out authenticate request?
			writeMsg(bufferedWriter, new AuthenticateRequest(idxSecret));
			Message authMsg;
			try {
				authMsg = readMsg(bufferedReader);
			} catch (JsonSerializationException e1) {
				writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
				return;
			}
			if(authMsg.getClass().getName()==WelcomeMsg.class.getName()){
				//
				try {
					authMsg = readMsg(bufferedReader);
				} catch (JsonSerializationException e1) {
					writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
					return;
				}
			}
			if(authMsg.getClass().getName()==AuthenticateReply.class.getName()){
				AuthenticateReply authReply = (AuthenticateReply) authMsg;
				if(!authReply.success){
					tgui.logError("authenticate failed!");
					return;
				} else {
					tgui.logInfo("authenticate succeed!");
					//send out a SearchRequest to Index Server, with details of a file to index
					tgui.logInfo("Sending a SearchRequest to Server.....");
					
					writeMsg(bufferedWriter,new SearchRequest(maxhits, keywords));
				
					//Get SearchReply from IdxServer
					// get a message
					Message msg;
					try {
						msg = readMsg(bufferedReader);
						String msgname = msg.getClass().getName();
						tgui.logDebug(msgname);
						if(msgname==SearchReply.class.getName()) {
							SearchReply searchReply = (SearchReply) msg;
							//tgui.logInfo(String.valueOf(searchReply.hits.length) + "!!!!!!!!!!!!!!!!!!");
							if(searchReply.hits.length == 0){
								tgui.logInfo("No Hit has been found!");
							} else {
								for(int i = 0; i < searchReply.hits.length; i++){
									FileMgr fileMgr;
									try {
										try {
											fileMgr = new FileMgr(searchReply.hits[i].filename);
											tgui.logInfo(searchReply.hits[i].filename + "has been Hit!");
											tgui.addSearchHit(searchReply.hits[i].filename, new SearchRecord(fileMgr.getFileDescr(), searchReply.seedCounts[i], idxAddress, idxPort, idxSecret, searchReply.hits[i].secret));	
										} catch (NoSuchAlgorithmException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
										} catch (NullPointerException e) {
										// TODO Auto-generated catch block
										tgui.logError("NullPointerExceoption");
										return;
									}
	
								}
							}
						}
					} catch (JsonSerializationException e1) {
						writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
						return;
					} 
				}
			}
			bufferedReader.close();
			bufferedWriter.close();
			socket.close();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//tgui.logError("searchIdxServer unimplemented");
	}

	/**
	 * Drop (stop) sharing the given file with the index server, using the provided secrets.
	 * @param relativePathname the filename relative to the `basedir`
	 * @param shareRecord describes the shared file to drop
	 * @return true if the sharing table entry should be removed in the gui, false otherwise
	 */
	@Override
	public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {
		//String filepath = this.basedir.concat('/' + relativePathname);
		// FileDescr fileDescr;
		
		Socket socket;
		try {
	        
			socket = new Socket(shareRecord.idxSrvAddress, shareRecord.idxSrvPort);
			//socket = new Socket(idxAddress.getHostName(), idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
			//no use of IdxSecret?? -----> first need to send out authenticate request?
			writeMsg(bufferedWriter, new AuthenticateRequest(shareRecord.idxSrvSecret));
			Message authMsg;
			try {
				authMsg = readMsg(bufferedReader);
			} catch (JsonSerializationException e1) {
				writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
				return false;
			}
			if(authMsg.getClass().getName()==WelcomeMsg.class.getName()){
				try {
					authMsg = readMsg(bufferedReader);
				} catch (JsonSerializationException e1) {
					writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
					return false;
				}
			}
			if(authMsg.getClass().getName()==AuthenticateReply.class.getName()){
				AuthenticateReply authReply = (AuthenticateReply) authMsg;
				if(!authReply.success){
					tgui.logError("authenticate failed!");
					return false;
				} else {
					tgui.logInfo("authenticate succeed!");
					//send out a SearchRequest to Index Server, with details of a file to index
					tgui.logInfo("Sending a DropRequest to Server.....");
					
					//public DropShareRequest(String filename, String fileMd5, String sharingSecret,int port) {
	
					//the port of peer
				
					writeMsg(bufferedWriter,new DropShareRequest(relativePathname, shareRecord.fileMgr.getFileDescr().getFileMd5(), shareRecord.sharerSecret, this.port));
				
					//Get SearchReply from IdxServer
					// get a message
					Message msg;
					try {
						msg = readMsg(bufferedReader);
					} catch (JsonSerializationException e1) {
						writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
						return false;
					}
					String msgname = msg.getClass().getName();
					if(msgname==DropShareReply.class.getName()) {
						DropShareReply dropReply = (DropShareReply) msg;
						if(dropReply.success)
							return true;
						else
						return false;
					}
					socket.close();
				}
			}
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//tgui.logError("dropShareWithIdxServer unimplemented");
		return false;
	}

	@Override
	public void downloadFromPeers(String relativePathname, SearchRecord searchRecord) throws IOException{
		
		//acts as peer server
		sharePeer serverPeer;
		
		serverPeer = new sharePeer(this.port,tgui,this.timeout);
		serverPeer.start();
    	
		String serverPeerAddress  = "localhost";
		int serverPeerPort = 3201;
		//String serverPeerSecret;

		//Ask the Index Server for the searchRecord information (ip, port, secret of the corresponding sharing Peer)
        Socket socket;
		try {
	        
			socket = new Socket(searchRecord.idxSrvAddress, searchRecord.idxSrvPort);
			//socket = new Socket(idxAddress.getHostName(), idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
			//no use of IdxSecret?? -----> first need to send out authenticate request?
			writeMsg(bufferedWriter, new AuthenticateRequest(searchRecord.idxSrvSecret));
			Message authMsg;
			try {
				authMsg = readMsg(bufferedReader);
			} catch (JsonSerializationException e1) {
				writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
				return;
			}
			if(authMsg.getClass().getName()==WelcomeMsg.class.getName()){
				try {
					authMsg = readMsg(bufferedReader);
				} catch (JsonSerializationException e1) {
					writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
					return;
				}
			}
            if(authMsg.getClass().getName()==AuthenticateReply.class.getName()){
				AuthenticateReply authReply = (AuthenticateReply) authMsg;
				if(!authReply.success){
					tgui.logError("authenticate failed!");
					return;
				} else {
					tgui.logInfo("authenticate succeed!");
					//send out a SearchRequest to Index Server, with details of a file to index
					tgui.logInfo("Sending a DropRequest to Server.....");
					
					//public DropShareRequest(String filename, String fileMd5, String sharingSecret,int port) {
	
					//the port of peer
				    //public LookupRequest(String filename, String fileMd5)
					writeMsg(bufferedWriter,new LookupRequest(relativePathname, searchRecord.fileDescr.getFileMd5()));
				
					//Get SearchReply from IdxServer
					// get a message
					Message msg;
					try {
						msg = readMsg(bufferedReader);
					} catch (JsonSerializationException e1) {
						writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
						return;
					}
					String msgname = msg.getClass().getName();
					if(msgname==LookupReply.class.getName()) {
						LookupReply lookupReply = (LookupReply) msg;
						//Question 1
						//直接取第一個hits就行,

						// for(int i = 0; i < lookupReply.hits.length; i++){
						if(lookupReply.hits.length == 0){
							tgui.logError("Something is Wrong!");
						} else {
							serverPeerAddress = lookupReply.hits[0].ip;
							serverPeerPort = lookupReply.hits[0].port;
							//serverPeerSecret = lookupReply.hits[0].secret;
						}
                    
						// }
                        
					}
					bufferedReader.close();
			        bufferedWriter.close();
					socket.close();
				}
			}
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			
			e1.printStackTrace();
		}
		//tgui.logError("dropShareWithIdxServer unimplemented");
		
        //Build socket to the Server Peer and send blockRequest
	
		Socket socket1;
		try {
			socket1 = new Socket(serverPeerAddress, serverPeerPort);
			InputStream inputStream = socket1.getInputStream();
			OutputStream outputStream = socket1.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
			//send blockRequest
			FileMgr fileMgr;
				try {
					fileMgr = new FileMgr(relativePathname);
					//Question 1: download blocks in a natural order
					for(int i = 0; i < fileMgr.getFileDescr().getNumBlocks(); i++ ){
						
						tgui.logInfo("Sending a BlockRequest to Peer " + serverPeerAddress + ":" + serverPeerPort);
						//Get the fileMD5
                        writeMsg(bufferedWriter,new BlockRequest(relativePathname, fileMgr.getFileDescr().getFileMd5(), i));
						//判斷是不是最後一個index, 決定發Goodbye
						if(i == fileMgr.getFileDescr().getNumBlocks() - 1){
							writeMsg(bufferedWriter,new Goodbye("Downlading Completed. Goodbye!"));
						}

						Message msg;
						try {
							msg = readMsg(bufferedReader);
						}catch (JsonSerializationException e1) {
							writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
							continue;
						}

						String msgname = msg.getClass().getName();
						tgui.logDebug(msgname);
						if(msg.getClass().getName()==BlockReply.class.getName()){
							BlockReply blockReply = (BlockReply) msg;
							//write block to the index, used for client Peer
							byte[] byteArrray = blockReply.bytes.getBytes();
							if(fileMgr.checkBlockHash(blockReply.blockIdx, byteArrray)){
								if(fileMgr.writeBlock(blockReply.blockIdx, byteArrray)){
									tgui.logInfo("Download Block" + blockReply.blockIdx + " Succeeded!" );
								} else{
									tgui.logError("Write Block" + blockReply.blockIdx + " to local Failed!");
									continue;
								}
							} else{
								tgui.logError("Block MD5 doesn't match!");
								tgui.logError("Download Block" + blockReply.blockIdx + " Failed!");
								continue;
							}
                            continue;
			            } else if(msg.getClass().getName()==Goodbye.class.getName()){
							bufferedReader.close();
							bufferedWriter.close();
                            socket1.close();
							return;
						} else {
				            tgui.logError("Invalide Reply!");
							continue;
			            }
					}
				} catch (NoSuchAlgorithmException | IOException e) {
					// TODO Auto-generated catch block
					tgui.logError("Invalid FileMgr");
					return;
				}
			
			//receive blockReply
			// Message msg;
			// try {
			// 	msg = readMsg(bufferedReader);
			// }catch (JsonSerializationException e1) {
			// 	writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
			// 	return;
			// }

			// String msgname = msg.getClass().getName();
			// tgui.logDebug(msgname);
			// if(msg.getClass().getName()==BlockReply.class.getName()){
			// 	BlockReply blockReply = (BlockReply) msg;
			// 	//write block to the index, used for client Peer
			// 	byte[] byteArrray = blockReply.bytes.getBytes();
			// 	if(fileMgr.checkBlockHash(blockReply.blockIdx, byteArrray)){
			// 		if(fileMgr.writeBlock(blockReply.blockIdx, byteArrray)){
			// 			tgui.logInfo("Download Block" + blockReply.blockIdx + "Succeeded!" );
			// 		} else{
			// 			tgui.logError("Download Block" + blockReply.blockIdx + "Failed!");
			// 		}
			//     } else{
			// 		tgui.logError("FileMD5 doesn't match!");
			// 	}

			// 	return;
			// } else {
			// 	tgui.logError("Invalide Message!");
			// }
					
		}		
		catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	 
	
	
    //this peer acts as the server, makes blockReply

		// try {
			
		// 	Socket socket2 = this.incomingConnections.take();
		// 	InputStream inputStream = socket2.getInputStream();
		// 	OutputStream outputStream = socket2.getOutputStream();
		// 	BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		// 	BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
		// 	//Process blockRequest
		// 	Message msg;
		// 	try {
		// 		msg = readMsg(bufferedReader);
		// 	}catch (JsonSerializationException e1) {
		// 		writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
		// 		return;
		// 	}
		// 		String msgname = msg.getClass().getName();
		// 		tgui.logDebug(msgname);
		// 	if(msg.getClass().getName()==BlockRequest.class.getName()){
		// 		BlockRequest blockRequest = (BlockRequest) msg;
		// 		//authReply.bytes
		// 		FileMgr fileMgr;
		// 		try {
		// 			fileMgr = new FileMgr(blockRequest.filename);
		// 			//fileMgr = new FileMgr(relativePathname, searchRecord.fileDescr);
        //             //check block availablity
		// 			if(fileMgr.checkFileHash()){
		// 				if(!fileMgr.isComplete()){
        //                     tgui.logError("NOT all blocks of the file are available, file is incomplete!");
		// 				    fileMgr = new FileMgr(blockRequest.filename, fileMgr.getFileDescr());
		// 				}
		// 			} else {
		// 				tgui.logError("file's MD5 hash  DOESN'T match the descriptor!");
		// 				return;
		// 			}
		// 			if(fileMgr.isBlockAvailable(blockRequest.blockIdx)){
		// 				//int numBytes = searchRecord.fileDescr.getNumBlockBytes(blockRequest.blockIdx);
        //                 byte[] bytes;
		// 				try {
		// 					bytes = fileMgr.readBlock(blockRequest.blockIdx);
		// 					String s = new String(bytes, StandardCharsets.UTF_8);
		// 				    writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, s)); 
						
		// 				} catch (BlockUnavailableException e) {
		// 					// TODO Auto-generated catch block
		// 					e.printStackTrace();
		// 				}
		// 				//write numBytes of BlockReples
		// 				// for(int i = 0; i < numBytes; i++){
		// 				//     writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, bytes[i])); 
		// 				// }
		// 			} else {
		// 				tgui.logError("This Block is NOT available now!");
		// 				return;
		// 			}
					
		// 		} catch (NoSuchAlgorithmException | IOException e) {
		// 			// TODO Auto-generated catch block
		// 			tgui.logError("Invalid message");
		// 		    return;
		// 	    } 
		// 	} else if(msg.getClass().getName()==Goodbye.class.getName()){
        //         bufferedReader.close();
		// 	    bufferedWriter.close();
		// 	    socket2.close();
		// 		return;
		// 	} else{
		// 		tgui.logError("Invalid message");
		// 		return;
		// 	}
	
		// } catch (InterruptedException e) {
		// 	// TODO Auto-generated catch block
		// 	e.printStackTrace();
		// }
		// //writeMsg(bufferedWriter,new BlockRequest(filepath, fileDescr.getFileMd5(), ));
 		// catch (IOException e) {
		// 	// TODO Auto-generated catch block
		// 	e.printStackTrace();
		// }
		
		//tgui.logError("downloadFromPeers unimplemented");
		try {
			serverPeer.join();
		} catch (InterruptedException e) {
			tgui.logError("Could not join with the server.");
		}
	
}

	private void writeMsg(BufferedWriter bufferedWriter,Message msg) throws IOException {
		tgui.logDebug("sending: "+msg.toString());
		bufferedWriter.write(msg.toString());
		bufferedWriter.newLine();
		bufferedWriter.flush();
	}
	
	private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
		String jsonStr = bufferedReader.readLine();
		if(jsonStr!=null) {
			Message msg = (Message) MessageFactory.deserialize(jsonStr);
			tgui.logDebug("received: "+msg.toString());
			return msg;
		} else {
			throw new IOException();
		}
		
	}
	
}
