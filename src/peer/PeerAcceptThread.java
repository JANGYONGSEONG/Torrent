package peer;

import java.util.logging.Logger;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * 토렌트 파일을 통해 다운로드한 파일을 공유하기 위해 peer는 server socket을 생성하며
 * 다수의 peer에게 동시에 공유하기 위해서 thread 사용.
 */
public class PeerAcceptThread implements Runnable {

	ServerSocket clientServer = null;
	Socket peer = null;
	
	String info_hash = "";
	
	PeerThread pt;
	Thread t;

	Logger logger = Logger.getLogger(String.valueOf(this.getClass()));
	
	public PeerAcceptThread(ServerSocket clientServer) {
		this.clientServer = clientServer;
	}

	public void run() {
		while (true) {
			try {
				peer = clientServer.accept();
				if (peer != null) {
					pt = new PeerThread(peer,info_hash,"ServerSocket");	
					t = new Thread(pt);
					t.start();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".Peer() =>" + "\n 발생원인 :" + e.getMessage());
			}
		}
	}
}
