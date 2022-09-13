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
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.textgui.ISharerGUI;


import comp90015.idxsrv.message.ErrorMsg;
import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;
import comp90015.idxsrv.message.BlockRequest;
import comp90015.idxsrv.message.BlockReply;
import comp90015.idxsrv.message.Goodbye;


public class sharePeer extends Thread{
    /*
	 * Some private variables.
	 */
    private IOThread ioThread;
	
	private LinkedBlockingDeque<Socket> incomingConnections;
	
	private ISharerGUI tgui;
	
	private int timeout;
	
	private int port;
	
	//private String secret;	
	
	
	/**
	 * The Server thread must be explicitly started after creating an instance. The
	 * Server starts an independent IOThread to accept connections.
	 * @param port
	 * @param socketTimeout
	 * @param tgui
	 * @throws IOException
	 */
 
	public sharePeer(
			ISharerGUI tgui,
			int socketTimeout
			) throws IOException {
				//this.port=port;
				this.tgui=tgui;
                this.timeout=socketTimeout;
				incomingConnections=new LinkedBlockingDeque<Socket>();
				ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
				ioThread.start();
	}
	
	@Override
	public void run() {
		tgui.logInfo("Server Peer thread running.");
		while(!isInterrupted()) {
			try {
				//retrieve the head of incomingConnections queue
				Socket socket = incomingConnections.take();
                // String ip=socket.getInetAddress().getHostAddress();
                // int port=socket.getPort();
                // tgui.logInfo("Receiving request from "+ ip + ":" + port);
				// InputStream inputStream = socket.getInputStream();
			    // OutputStream outputStream = socket.getOutputStream();
			    // BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			    // BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
                // //Process blockRequest

                // Message msg;
                // try {
                //     msg = readMsg(bufferedReader);
                // }catch (JsonSerializationException e1) {
                //     writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
                //     return;
                // }
                //     String msgname = msg.getClass().getName();
                //     tgui.logDebug(msgname);
                // if(msg.getClass().getName()==BlockRequest.class.getName()){
                //     BlockRequest blockRequest = (BlockRequest) msg;
                //     //authReply.bytes
                //     FileMgr fileMgr;
                //     try {
                //         fileMgr = new FileMgr(blockRequest.filename);
                //         //fileMgr = new FileMgr(relativePathname, searchRecord.fileDescr);
                //         //check block availablity
                //         if(fileMgr.checkFileHash()){
                //             if(!fileMgr.isComplete()){
                //                 tgui.logError("NOT all blocks of the file are available, file is incomplete!");
                //                 //fileMgr = new FileMgr(blockRequest.filename, fileMgr.getFileDescr());
                //             }
                //         } else {
                //             tgui.logError("file's MD5 hash  DOESN'T match the descriptor!");
                //             return;
                //         }
                //         if(fileMgr.isBlockAvailable(blockRequest.blockIdx)){
                //             //int numBytes = searchRecord.fileDescr.getNumBlockBytes(blockRequest.blockIdx);
                //             byte[] bytes;
                //             try {
                //                 bytes = fileMgr.readBlock(blockRequest.blockIdx);
                //                 String s = new String(bytes, StandardCharsets.UTF_8);
                //                 writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, s)); 
                //                 tgui.logInfo("Sending BlockReply" + blockRequest.filename + " " + String.valueOf(blockRequest.blockIdx));
                //             } catch (BlockUnavailableException e) {
                //                 // TODO Auto-generated catch block
                //                 e.printStackTrace();
                //             }
                //             //write numBytes of BlockReples
                //             // for(int i = 0; i < numBytes; i++){
                //             //     writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, bytes[i])); 
                //             // }
                //         } else {
                //             tgui.logError("This Block is NOT available now!");
                //             return;
                //         }
                        
                //     } catch (NoSuchAlgorithmException | IOException e) {
                //         // TODO Auto-generated catch block
                //         tgui.logError("Invalid message");
                //         return;
                //     } 
                // } else if(msg.getClass().getName()==Goodbye.class.getName()){
                //     bufferedReader.close();
                //     bufferedWriter.close();
                //     socket.close();
                    
                // } else{
                //     tgui.logError("Invalid message");
                //     return;
                // }
			    processRequest(socket);
                socket.close();
			} catch (InterruptedException e) {
				tgui.logWarn("Server interrupted.");
				break;
			} catch (IOException e) {
				tgui.logWarn("Server received io exception on socket.");
			}
		}
		tgui.logInfo("Server thread waiting for IO thread to stop...");
		ioThread.interrupt();
		try {
			ioThread.join();
		} catch (InterruptedException e) {
			tgui.logWarn("Interrupted while joining with IO thread.");
		}
		tgui.logInfo("Server Peer thread completed.");
	}

    private void processRequest(Socket socket) throws IOException {
        String ip=socket.getInetAddress().getHostAddress();
        int port=socket.getPort();
                tgui.logInfo("Receiving request from "+ ip + ":" + port);
				InputStream inputStream = socket.getInputStream();
			    OutputStream outputStream = socket.getOutputStream();
			    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			
                //Process blockRequest

                Message msg;
                try {
                    msg = readMsg(bufferedReader);
                }catch (JsonSerializationException e1) {
                    writeMsg(bufferedWriter,new ErrorMsg("Invalid message"));
                    return;
                }
                    String msgname = msg.getClass().getName();
                    tgui.logDebug(msgname);
                if(msg.getClass().getName()==BlockRequest.class.getName()){
                    BlockRequest blockRequest = (BlockRequest) msg;
                    //authReply.bytes
                    FileMgr fileMgr;
                    try {
                        fileMgr = new FileMgr(blockRequest.filename);
                        //fileMgr = new FileMgr(relativePathname, searchRecord.fileDescr);
                        //check block availablity
                        if(fileMgr.checkFileHash()){
                            if(!fileMgr.isComplete()){
                                tgui.logError("NOT all blocks of the file are available, file is incomplete!");
                                fileMgr = new FileMgr(blockRequest.filename, fileMgr.getFileDescr());
                            }
                        } else {
                            tgui.logError("file's MD5 hash  DOESN'T match the descriptor!");
                           
                            return;
                        }
                        if(fileMgr.isBlockAvailable(blockRequest.blockIdx)){
                            //int numBytes = searchRecord.fileDescr.getNumBlockBytes(blockRequest.blockIdx);
                            byte[] bytes;
                            try {
                                bytes = fileMgr.readBlock(blockRequest.blockIdx);
                                String s = new String(bytes, StandardCharsets.UTF_8);
                                writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, s)); 
                                tgui.logInfo("Sending BlockReply" + blockRequest.filename + " " + String.valueOf(blockRequest.blockIdx));
                                processRequest(socket);
                            } catch (BlockUnavailableException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            //write numBytes of BlockReples
                            // for(int i = 0; i < numBytes; i++){
                            //     writeMsg(bufferedWriter, new BlockReply(blockRequest.filename, blockRequest.fileMd5, blockRequest.blockIdx, bytes[i])); 
                            // }
                        } else {
                            tgui.logError("This Block is NOT available now!");
                            processRequest(socket);
                        }
                        
                    } catch (NoSuchAlgorithmException | IOException e) {
                        // TODO Auto-generated catch block
                        tgui.logError("Invalid message");
                        return;
                    } 
                } else if(msg.getClass().getName()==Goodbye.class.getName()){
                    bufferedReader.close();
                    bufferedWriter.close();
                    //socket.close();
                    return;
                } else{
                    tgui.logError("Invalid message");
                    return;
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
