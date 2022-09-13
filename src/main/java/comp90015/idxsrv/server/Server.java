package comp90015.idxsrv.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;

import java.util.concurrent.LinkedBlockingDeque;

import java.nio.charset.StandardCharsets;

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
import comp90015.idxsrv.server.IndexMgr.RETCODE;
import comp90015.idxsrv.textgui.ITerminalLogger;


/**
 * Server protocol implementation for the Index Server, which extends thread
 * and processes an unbounded number of incoming connections until it is interrupted.
 * @author aaron
 *
 */
public class Server extends Thread {
	
	/*
	 * Some private variables.
	 */
	
	private IndexMgr indexMgr;
	
	private LinkedBlockingDeque<Socket> incomingConnections;
	
	private IOThread ioThread;
	
	private String welcome;
	
	private String secret;
	
	private ITerminalLogger logger;
	
	/**
	 * The Server thread must be explicitly started after creating an instance. The
	 * Server starts an independent IOThread to accept connections.
	 * @param port
	 * @param address
	 * @param welcome
	 * @param dir
	 * @param secret
	 * @param socketTimeout
	 * @param logger
	 * @throws IOException
	 */
	public Server(int port,
			InetAddress address,
			String welcome,
			String dir,
			String secret,
			int socketTimeout,
			ITerminalLogger logger) throws IOException {
				this.welcome=welcome;
				this.secret=secret;
				this.logger=logger;
				indexMgr = new IndexMgr();
				incomingConnections=new LinkedBlockingDeque<Socket>();
				ioThread = new IOThread(port,incomingConnections,socketTimeout,logger);
				ioThread.start();
	}
	
	@Override
	public void run() {
		logger.logInfo("Server thread running.");
		while(!isInterrupted()) {
			try {
				//retrieve the head of incomingConnections queue
				Socket socket = incomingConnections.take();
				processRequest(socket);
				socket.close();
			} catch (InterruptedException e
			) {
				logger.logWarn("Server interrupted.");
				break;
			} catch (IOException e) {
				logger.logWarn("Server received io exception on socket.");
			}
		}
		logger.logInfo("Server thread waiting for IO thread to stop...");
		ioThread.interrupt();
		try {
			ioThread.join();
		} catch (InterruptedException e) {
			logger.logWarn("Interrupted while joining with IO thread.");
		}
		logger.logInfo("Server thread completed.");
	}
	
	
	/**
	 * This method is essentially the "Session Layer" logic, where the session is
	 * short since it consists of exactly one request on the socket, then the socket
	 * is closed.
	 * @param socket
	 * @throws IOException
	 */
	private void processRequest(Socket socket) throws IOException {
		//解析出peer的ip
		String ip=socket.getInetAddress().getHostAddress();
		int port=socket.getPort();
		logger.logInfo("Server processing request on connection "+ip);
		InputStream inputStream = socket.getInputStream();
		OutputStream outputStream = socket.getOutputStream();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
		
		/*
		 * Follow the synchronous handshake protocol.
		 */
		
		// write the welcome
		//message content = the object of WelcomeMsg(), in which the constructor let the passed argument message to be the message content
		//so in this case, msg content = passed 實參 welcome, again this welcome is the member variable of this class Server, so when initialising this class(which means to create an object of this class Server -> in IdxSrv.java) the real argument(actual msg content) is passing
		writeMsg(bufferedWriter,new WelcomeMsg(welcome));
		
		// get a message
		Message msg;
		try {
			msg = readMsg(bufferedReader);
		} catch (JsonSerializationException e1) {
			writeMsg(bufferedWriter,new ErrorMsg("NON-jsonSerialization message"));
			return;
		}
		
		// check it is an authenticate request
		//getClass() returns the runtime class of the object(就直接得到運行時的class name)， getName() returns the class name
		//this ‘if’ could know whether this msg is an Authenticate Request or not, 只要它們 class name相同
		
		if(msg.getClass().getName()==AuthenticateRequest.class.getName()) {
			//不太懂爲什麽要轉化成另一個AuthenticateRequest ar
			AuthenticateRequest ar = (AuthenticateRequest) msg;
		    //than check if the secret matches, secret used for AuthenticateRequest matches the secret of this Server class
			if(!ar.secret.equals(this.secret)) {
				//if not match, Server sends an AuthenticateReply saying that the authentication fails
				writeMsg(bufferedWriter,new AuthenticateReply(false));
				return;
			} else {
				writeMsg(bufferedWriter,new AuthenticateReply(true));
			}
		} else {
			//因爲Server只接受AuthenticateRequest from clients, when in handshake period
			writeMsg(bufferedWriter,new ErrorMsg("Expecting AuthenticateRequest"));
			return;
		}
		
		/*
		 * Now get the request and process it. This is a single-request-per-connection
		 * protocol.
		 */
		
		// get the request message
		try {
			msg = readMsg(bufferedReader);
		} catch (JsonSerializationException e) {
			writeMsg(bufferedWriter,new ErrorMsg("NON-jsonSerialization message"));
			return;
		}
		
		// process the request message
		String msgname = msg.getClass().getName();
		if(msgname==ShareRequest.class.getName()) {
			processShareCmd(bufferedWriter,(ShareRequest) msg,ip,port);
		} else if(msgname==DropShareRequest.class.getName()) {
			processDropCmd(bufferedWriter,(DropShareRequest) msg,ip,port);
		} else if(msgname==SearchRequest.class.getName()) {
			processSearchCmd(bufferedWriter,(SearchRequest) msg,ip,port);
		} else if(msgname==LookupRequest.class.getName()) {
			processLookupCmd(bufferedWriter,(LookupRequest) msg,ip,port);
		} else {
			writeMsg(bufferedWriter,new ErrorMsg("Expecting a request message"));
		}
		
		// close the streams
		bufferedReader.close();
		bufferedWriter.close();
	}
	
	/*
	 * Methods to process each of the possible requests.
	 */
	
	//處理ShareRequest
	private void processShareCmd(BufferedWriter bufferedWriter,ShareRequest msg,String ip, int port) throws IOException {
		//if secret doesn't match, write error msg; if matches do the share() -> add the element to be shared
		if(indexMgr.share(ip, msg.port, msg.fileDescr, msg.filename, msg.sharingSecret)==RETCODE.FAILEDSECRET) {
			writeMsg(bufferedWriter,new ErrorMsg("Failed sharing secret"));
		} else {
			//then send the msg with the content == number of elements corresponds with the file the received ShareRequest wants to share
			//就是IndexElement list裏面，和從client接收到的這個ShareRequest裏包含的文件 所有匹配的elements == 所有sharing同樣的file的peers數量
			Integer numSharers = indexMgr.lookup(msg.filename,msg.fileDescr.getFileMd5()).size();
			writeMsg(bufferedWriter,new ShareReply(numSharers));
		}			
	}
	
	//處理DropShareRequest
	private void processDropCmd(BufferedWriter bufferedWriter,DropShareRequest msg,String ip, int port) throws IOException {
		//這裏爲啥不把drop()直接寫進判斷語句呢？
		/*if(indexMgr.drop(ip, msg.port, msg.filename, msg.fileMd5, msg.sharingSecret)==RETCODE.FAILEDSECRET) {
			writeMsg(bufferedWriter,new ErrorMsg("Failed secret"));
		} else if(如果這裏繼續使用同樣的判斷語句，drop()就會被執行twice，第二次肯定是Not found了（如果只有一個對應的msg），所以得把判斷Not found放在第一個判斷中)
		
		if(indexMgr.drop(ip, msg.port, msg.filename, msg.fileMd5, msg.sharingSecret)==RETCODE.INVALID) {
			writeMsg(bufferedWriter,new ErrorMsg("Not found"));
		} else if(indexMgr.drop(ip, msg.port, msg.filename, msg.fileMd5, msg.sharingSecret)==RETCODE.FAILEDSECRET)
			但實際還是會執行兩次，如果有多個對應的msg但我們只想drop一個的話，就會造成錯誤，所以直接把drop單獨寫出來		
		*/
		RETCODE retcode = indexMgr.drop(ip, msg.port, msg.filename, msg.fileMd5, msg.sharingSecret);
		if(retcode==RETCODE.FAILEDSECRET) {
			writeMsg(bufferedWriter,new ErrorMsg("Failed secret"));
		} else if(retcode==RETCODE.INVALID) {
			writeMsg(bufferedWriter,new ErrorMsg("Not found"));
		} else {
			writeMsg(bufferedWriter,new DropShareReply(true));
		}			
	}
	
	//處理SearchRequest
	private void processSearchCmd(BufferedWriter bufferedWriter,SearchRequest msg,String ip,int port) throws IOException {
		for(int i=0;i<msg.keywords.length;i++) {
			msg.keywords[i]=msg.keywords[i].toLowerCase();
		}
		ArrayList<IndexElement> hits = indexMgr.search(msg.keywords, msg.maxhits);
		//the length of seedCounts equals the length of all hitsElement
		Integer[] seedCounts = new Integer[hits.size()];
		for(int i=0;i<hits.size();i++) {
			//the element of seedCounds is lookup() of every hitted file
		
			//lookup(): Return the set of all elements corresponding to a given file MD5 hash.
			seedCounts[i]=indexMgr.lookup(hits.get(i).filename,hits.get(i).fileDescr.getFileMd5()).size();
		}
		writeMsg(bufferedWriter,new SearchReply(hits,seedCounts));
	}
	
	//處理LookupRequest
	private void processLookupCmd(BufferedWriter bufferedWriter,LookupRequest msg,String ip,int port) throws IOException {
		logger.logInfo("Processing LookUpRequest");
		HashSet<IndexElement> hits = indexMgr.lookup(msg.filename,msg.fileMd5);
		writeMsg(bufferedWriter,new LookupReply(new ArrayList<IndexElement>(hits)));
	}
	
	/*
	 * Methods for writing and reading messages.
	 */
	
	private void writeMsg(BufferedWriter bufferedWriter,Message msg) throws IOException {
		logger.logDebug("sending: "+msg.toString());
		bufferedWriter.write(msg.toString());
		bufferedWriter.newLine();
		bufferedWriter.flush();
	}
	
	private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
		String jsonStr = bufferedReader.readLine();
		if(jsonStr!=null) {
			Message msg = (Message) MessageFactory.deserialize(jsonStr);
			logger.logDebug("received: "+msg.toString());
			return msg;
		} else {
			throw new IOException();
		}
	}
}
