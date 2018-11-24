package tracker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import common.Common;

public class TrackerThread implements Runnable {
	static HashMap<String, List<String>> swarmHashMap;
	
	Socket client = null;

	String info_hash = "";

	BufferedReader ois = null;
	BufferedWriter oos = null;

	Logger logger = Logger.getLogger(String.valueOf(this.getClass()));

	public TrackerThread(Socket client, HashMap<String, List<String>> swarmHashMap) {
		this.client = client;
		this.swarmHashMap = swarmHashMap;
		try {
			ois = new BufferedReader(new InputStreamReader(client.getInputStream()));
			oos = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF8"));
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".TrackerThread() =>" + "\n 발생원인 : read,write buffer create fail"
					+ e.getMessage());
		}
	}

	public void run() {
		String messageName = "";
		try {
			while ((messageName = ois.readLine()) != null) {
				if (messageName.equals(Common.InformCreateFileMessage)) {
					getInformCreateFile();
				} else if (messageName.equals(Common.TrackerRequestMessage)) {
					getTrackerRequest();
					sendTrackerResponse();
				}
				System.out.println("-----------------------------------------------------");
				System.out.println("trakcer on");
				System.out.println("-----------------------------------------------------");
			}

		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".run() =>" + "\n 발생원인 :" + e.getMessage());
		}
	}

	/*
	 * tracker는 토렌트 파일을 만든 user로부터 message를 받음으로써 
	 * 해당 토렌트 파일의 info_hash값과 seeder의 ip주소와 파일을 공유할 때 사용하는 port번를 알 수 있다.
	 */
	private void getInformCreateFile() {

		List<String> swarm = new ArrayList<String>();
		HashMap<String, String> messageHashMap = new HashMap<String, String>();

		FileWriter fw = null;
		BufferedWriter bw = null;
		try {
			parseMessage(messageHashMap);

			fw = new FileWriter(Common.swarmFilePath
					+ messageHashMap.get(Common.InformCreateFile.Info_Hash.toString()) + ".txt");
			synchronized(fw) {
				bw = new BufferedWriter(fw);
				swarm.add(messageHashMap.get(Common.InformCreateFile.IP.toString()));
	
				for (String ip : swarm) {
					bw.write(ip + "\n");
				}
				bw.flush();
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getTorrentFileCreator() =>" + "\n 발생원인 :" + e.getMessage());
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
				if (fw != null) {
					fw.close();
				}
			} catch (IOException e) {
				logger.info(this.getClass().getName() + ".getTorrentFileCreator(): writer close fail =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}
	}

	/*
	 * tracker는 토렌트 파일을 실행한 user로부터 info_hash값을 전송받고 
	 * 해당 info_hash를 통해서 peer,seeder 리스트 찾아내고 swarm에 저장한다.
	 * tracker는 토렌트 파일을 실행한 user가 swarm에 저장되어 있는 user가 아닌 새로운
	 * user인 경우 해당 user의 IP를 swarm에 저장한다.
	 */
	private void getTrackerRequest() {
		List<String> swarm = new ArrayList<String>();
		HashMap<String, String> messageHashMap = new HashMap<String, String>();

		FileReader fr = null;
		BufferedReader br = null;

		FileWriter fw = null;
		BufferedWriter bw = null;

		parseMessage(messageHashMap);

		info_hash = messageHashMap.get(Common.TrackerRequest.Info_Hash.toString());

		try {
			fr = new FileReader(
					Common.swarmFilePath + info_hash + ".txt");
			synchronized(fr) {
				br = new BufferedReader(fr);
	
				String str = "";
				while ((str = br.readLine()) != null) {
					swarm.add(str);
				}
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getTrackerRequest(): info_hash file read fail =>" + "\n 발생원인 :"
					+ e.getMessage());
		} finally {
			try {
				if (br != null) {
					br.close();
				}
				if (fr != null) {
					fr.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".getTrackerRequest(): writer close fail =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}

		try {
			fw = new FileWriter(
					Common.swarmFilePath + info_hash + ".txt", true);
			synchronized(fw) {
				bw = new BufferedWriter(fw);
	
				String ip = messageHashMap.get(Common.TrackerRequest.IP.toString());
				if (!swarm.contains(ip)) {
					swarm.add(ip);
					bw.write(ip + "\n");
					bw.flush();
				}
			}
			synchronized (swarmHashMap) {
				swarmHashMap.put(info_hash, swarm);
			}

		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getTrackerRequest() =>" + "\n 발생원인 :" + e.getMessage());
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
				if (fw != null) {
					fw.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".getTrackerRequest(): writer close fail =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}
	}

	/*
	 * getInformCreateFile(), getTrackerRequest()에서 사용
	 */
	private void parseMessage(HashMap<String, String> hashMap) {
		int pos = 0;
		String str = "";
		try {
			while ((str = ois.readLine()) != null) {
				if (str.equals("End")) {
					break;
				}
				pos = str.indexOf(":");
				hashMap.put(str.substring(0, pos), str.substring(pos + 1));
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".parseMessage() =>" + "\n 발생원인 :" + e.getMessage());
		}
	}

	/*
	 * TrackerRequest message를 받은 Tracker는 user에게 peer,seeder 리스트가 포함
	 * TrackerResponse message를 보낸다.
	 */
	private void sendTrackerResponse() {
		try {
			oos.write(Common.TrackerResponseMessage+"\n");
			for (Common.TrackerResponse parameter : Common.TrackerResponse.values()) {
				switch (parameter) {
				case Complete:

					break;
				case Downloaded:

					break;
				case Incomplete:

					break;
				case Interval:

					break;
				case MinInterval:

					break;
				case Peers:
					oos.write(Common.TrackerResponse.Peers.toString()+":");
					synchronized (swarmHashMap) {
						for (String ip : swarmHashMap.get(info_hash)) {
							oos.write(ip + ",");
						}
					}
					oos.write("\n");
					break;
				}
			}
			oos.write(Common.EndMessage+"\n");
			oos.flush();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendTrackerResponse() =>" + "\n 발생원인 :" + e.getMessage());
		}
	}
}
