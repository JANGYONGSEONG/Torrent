package tracker;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.List;
import tracker.TrackerThread;
import common.Common;

public class Tracker {
	private static HashMap<String, List<String>> swarmHashMap;
	static ServerSocket tracker = null;
	
	Socket client = null;

	TrackerThread tt;
	Thread t;

	Logger logger = Logger.getLogger(String.valueOf(this.getClass()));

	private Tracker() {
		try {
			tracker = new ServerSocket(Common.TrackerPort);
			swarmHashMap = new HashMap<String, List<String>>(); // key:InfoHash value:swram(peer list)

			while (true) {
				client = tracker.accept();
				if (client != null) {
					tt = new TrackerThread(client, swarmHashMap);
					t = new Thread(tt);
					t.start();
				}
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".Tracker() =>" + "\n 발생원인 :" + e.getMessage());
		}
	}

	public static void main(String[] args) {
		new Tracker();
	}

}
