package peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.FileReader;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.net.Socket;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import common.Common;

public class Peer {
	static String client_user_id;
	static String client_ipAddress;
	static ServerSocket clientServer = null;
	static Socket client = null;
	static int clientServerPort = -1;

	PeerAcceptThread pat;
	PeerThread pt;
	Thread t;

	String info_hash = "";
	List<String> swarm;

	Logger logger = Logger.getLogger(String.valueOf(this.getClass()));

	private Peer(String id, String ip) {
		client_user_id = id;
		client_ipAddress = ip;

		setPeerServer();

		pat = new PeerAcceptThread(clientServer);
		t = new Thread(pat);
		t.start();

		String menu = "";
		String torrentFileName = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			menu = getMenu(br);
			switch (menu) {
			case "Make torrentfile":
				torrentFileName = getTorrentFileName(br);
				makeFile(torrentFileName, client_user_id);
				break;
			case "Run torrentfile":
				torrentFileName = getTorrentFileName(br);
				runFile(torrentFileName);
				break;
			case "Quit":
				System.exit(1);
			default:
				System.out.println("잘못된 입력입니다...\n");
			}

		}
	}

	/*
	 * 토렌트 파일을 통해 다운로드한 파일을 공유하기 위해 peer는 server socket을 생성. 
	 * peer의 server socket port번호는 이후에도 동일한 port번호를 사용하기 위해서 파일에 저장한다. 
	 * port번호는 sendInformCreateFile()과 sendTrackerRequest()에서 IP와 함께 tracker에게 전송되며
	 * tracker는 전송된 port번호를 swarm을 전송할 때 IP와 함께 해당 IP의 port번호를 함께 전송한다.
	 */
	private void setPeerServer() {

		FileReader fr = null;
		BufferedReader br = null;

		FileWriter fw = null;
		BufferedWriter bw = null;

		if (findFile(new File(Common.peerServerPortFilePath + client_user_id ), client_ipAddress + ".txt") == -1) { 
			// 프로그램을 처음 사용할 때 port번호를 지정해준다.
			try {
				new File(Common.peerServerPortFilePath + client_user_id).mkdirs();
				fw = new FileWriter(
						Common.peerServerPortFilePath + client_user_id + "/" + client_ipAddress + ".txt");
				bw = new BufferedWriter(fw);
				try {
					clientServer = new ServerSocket(0);
					clientServerPort = clientServer.getLocalPort();
				} catch (Exception e) {
					logger.info(this.getClass().getName() + ".setPeerServer(): server port create section =>"
							+ "\n 발생원인 :" + e.getMessage());
				}
				bw.write(clientServerPort + "\n");
				bw.flush();
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".setPeerServer(): write server port in file section  =>"
						+ "\n 발생원인 :" + e.getMessage());
			} finally {
				try {
					if (bw != null) {
						bw.close();
					}
					if (fw != null) {
						fw.close();
					}
				} catch (Exception e) {
					logger.info(this.getClass().getName() + ".setPeerServer(): write close section  =>" + "\n 발생원인 :"
							+ e.getMessage());
				}
			}
		} else {
			// 지정된 port번호가 있는 경우 해당 port번호를 사용한다.
			try {
				fr = new FileReader(Common.peerServerPortFilePath + client_user_id + "/" + client_ipAddress + ".txt");
				br = new BufferedReader(fr);
				String str = "";
				if ((str = br.readLine()) != null) {
					clientServerPort = Integer.parseInt(str);
					clientServer = new ServerSocket(clientServerPort);
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".setPeerServer(): read server port in file section =>"
						+ "\n 발생원인 :" + e.getMessage());
			} finally {
				try {
					if (br != null) {
						br.close();
					}
					if (fr != null) {
						fr.close();
					}
				} catch (Exception e) {
					logger.info(this.getClass().getName() + ".setPeerServer(): read close section =>" + "\n 발생원인 :"
							+ e.getMessage());
				}
			}
		}

	}

	/*
	 * 
	 */
	private String getMenu(BufferedReader br) {
		String menu = "";
		try {
			System.out.println("-----------------------------------------------------");
			System.out.println("메뉴 입력) \nMake torrentfile \nRun torrentfile \nQuit");
			System.out.println("-----------------------------------------------------");
			menu = br.readLine();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getMenu() => " + "\n 발생원인 :" + e.getMessage());
		}
		return menu;
	}

	/*
	 * 
	 */
	private String getTorrentFileName(BufferedReader br) {
		String torrentFileName = "";
		try {
			System.out.print("torrentfilename: ");
			torrentFileName = br.readLine();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getTorrentFileName() => " + "\n 발생원인 :" + e.getMessage());
		}
		return torrentFileName;
	}

	/*
	 * 
	 */
	private void makeFile(String torrentFileName, String user_id) {
		FileWriter fw = null;
		BufferedWriter bw = null;

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		StringBuffer sb;

		
		String info_hash = "";
		String directory = "";
		String contentFileName = "";
		int pieceLength = 0;
		long fileLength = -1;
		
		String comment = "";

		String filePath = "";
		
		try {
			fw = new FileWriter(Common.torrentFilePath + torrentFileName + ".txt");
			bw = new BufferedWriter(fw);
			sb = new StringBuffer();
			for (Common.TorrentFileContents content : Common.TorrentFileContents.values()) {
				switch (content) {
				case TorrentFileName:
					sb.append(content.toString() + ":" + torrentFileName + "\n");
					break;
				case TrackerURL:
					sb.append(content.toString() + ":localhost:" + Common.TrackerPort + "\n");
					break;
				case Directory:
					System.out.print(content.toString() + ": ");
					while ((directory = br.readLine()).isEmpty()) {
						System.out.println("내용을 입력해주세요....\n" + content.toString() + ": ");
					}
					sb.append(content.toString() + ":" + directory + "\n");
					break;
				case CreatedOn:
					sb.append(content.toString() + ":" + new Date() + "\n");
					break;
				case CreatedBy:
					sb.append(content.toString() + ":" + user_id + "\n");
					break;
				case Comment:
					System.out.print(content.toString() + ": ");
					while ((comment = br.readLine()).isEmpty()) {
						System.out.println("내용을 입력해주세요....\n" + content.toString() + ": ");
					}
					sb.append(content.toString() + ":" + comment + "\n");
					break;
				case ContentFileNames:
					do {
						System.out.println("원하는 파일의 경로를 입력해주세요...");
						filePath = br.readLine();
						System.out.println("원하는 파일의 이름을 입력해주세요...");
						contentFileName = br.readLine();
						if ((fileLength = findFile(new File(filePath), contentFileName)) != -1) {
							sb.append(content.toString() + ":" + contentFileName + "\n");
							sb.append(Common.TorrentFileContents.FileLength.toString() + ":" + fileLength + "\n");
						}
					} while (fileLength == -1);
					break;
				case PieceLength:
					pieceLength = (int) fileLength / 1400; // 1400개의 분할이 p2p상에서 효율적이라는 글을 보고 test를 위한 sample값으로 설정
					sb.append(content.toString() + ":" + pieceLength + "\n");
					break;
				case Private:
					break;
				case Info_Hash:
					String str = directory + contentFileName + pieceLength;
					info_hash = createHash(str);
					sb.append(content.toString() + ":" + info_hash + "\n");
					break;
				}
			}
			bw.write(sb.toString());
			bw.flush();

			sendInformCreateFile(info_hash, client_ipAddress);

			makeInfoHashFile(info_hash, directory, contentFileName, pieceLength, fileLength);

			replaceContentFile(filePath, contentFileName, directory);

			System.out.println("-----------------------------------------------------");
			System.out.println("파일이 생성되었습니다.");
			System.out.println("-----------------------------------------------------");
		} catch (Exception e) {
			System.out.println("-----------------------------------------------------");
			System.out.println("파일이 생성되지 못했습니다.");
			System.out.println("-----------------------------------------------------");
			logger.info(this.getClass().getName() + ".makeFile(): write torrent file section => " + "\n 발생원인 :"
					+ e.getMessage());
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
				if (fw != null) {
					fw.close();
				}
			} catch (IOException e) {
				logger.info(this.getClass().getName() + ".makeFile(): write close section =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}
	}

	/*
	 * info_hash가 directory 이름, contentFile 이름, piece 길이로 생성되는데 
	 * peer가 파일을 다운로드한 뒤 directory 이름이나 contetnFile 이름을 변경 시 해당 파일을 찾지못하므로 
	 * 일단은 share file directory 내부에서만 file 공유를 하는 제한을 두기로 했다.
	 */
	private void replaceContentFile(String filePath, String contentFileName, String directory) {
		File file = null;
		FileInputStream fis = null;
		File shareFile = null;
		FileOutputStream fos = null;
		try {
			file = new File(filePath + "/" + contentFileName);
			fis = new FileInputStream(file);
			new File(Common.shareFilePath + client_user_id + "/" + directory).mkdirs();
			shareFile = new File(
					Common.shareFilePath + client_user_id + "/" + directory + "/" + contentFileName);
			fos = new FileOutputStream(shareFile);
			int data = 0;
			while ((data = fis.read()) != -1) {
				fos.write(data);
			}
			fos.flush();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".replaceContentFile(): file copy section =>" + "\n 발생원인 :"
					+ e.getMessage());
		} finally {
			try {
				if (fos != null) {
					fos.close();
				}
				if (fis != null) {
					fis.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".replaceContentFile(): file I/O stream close section =>"
						+ "\n 발생원인 :" + e.getMessage());
			}
		}
	}

	/*
	 * 토렌트 파일을 만든 user가 tracker에게 message를 보냄으로써 
	 * tracker가 해당 토렌트 파일의 info_hash값과 seeder의 IP와 port번호를 알 수 있게한다.
	 */
	private void sendInformCreateFile(String info_hash, String ipAddress) {
		BufferedWriter oos = null;
		try {
			client = new Socket(ipAddress, Common.TrackerPort);
			oos = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF8"));
			oos.write(Common.InformCreateFileMessage + "\n");
			oos.write(Common.InformCreateFile.Info_Hash.toString() + ":" + info_hash + "\n");
			oos.write(Common.InformCreateFile.IP.toString() + ":" + ipAddress + ":" + clientServerPort + "\n");
			oos.flush();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".informToTracker()  =>" + "\n 발생원인 :" + e.getMessage());
		} finally {
			try {
				if (oos != null) {
					oos.close();
				}
				if (client != null) {
					client.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".informToTracker(): write close section =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}
	}

	/*
	 * peer가 다른 peer로부터 파일 공유 요청이 왔을 때 
	 * 해당 파일 또는 파일 조각을 가지고 있는지 확인하기 위한 파일을 생성한다.
	 */
	private void makeInfoHashFile(String info_hash, String directory, String contentFileName, int pieceLength,
			long fileLength) {
		File file = null;

		FileWriter fw = null;
		BufferedWriter bw = null;

		try {
			file = new File(Common.infoHashFilePath + client_user_id);
			if (!file.isDirectory()) {
				file.mkdir();
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".makeInfoHashFile():directory 생성 실패  => " + "\n 발생원인 :"
					+ e.getMessage());
		}

		try {
			fw = new FileWriter(Common.infoHashFilePath + client_user_id + "/" + info_hash + ".txt");
			bw = new BufferedWriter(fw);
			bw.write(Common.InfoHashFileContents.Directory.toString() + ":" + directory + "\n");
			bw.write(Common.InfoHashFileContents.ContentFileName.toString() + ":" + contentFileName + "\n");
			bw.write(Common.InfoHashFileContents.PieceLength.toString() + ":" + pieceLength + "\n");
			bw.write(Common.InfoHashFileContents.FileLength.toString() + ":" + fileLength + "\n");
			bw.flush();
		} catch (Exception e) {
			logger.info(
					this.getClass().getName() + ".makeInfoHashFile():file 생성 실패 => " + "\n 발생원인 :" + e.getMessage());

		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
				if (fw != null) {
					fw.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".makeInfoHashFile(): write close section =>" + "\n 발생원인 :"
						+ e.getMessage());
			}
		}
	}

	/*
	 * 디렉토리 내부에 찾는 파일이 존재하면 파일의 길이를 반환, 존재하지 않으면 -1을 반환
	 */
	private long findFile(File fileDirectory, String name) {
		File[] list = fileDirectory.listFiles();
		if (list != null) {
			for (File file : list) {
				if (name.equals(file.getName())) {
					return file.length();
				}
			}
		}

		return -1;
	}

	/*
	 * 
	 */
	private String createHash(String str) {
		String SHA = "";
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.reset();
			try {
				digest.update(str.getBytes("utf8"));
				byte byteData[] = digest.digest();
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < byteData.length; i++) {
					sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
				}
				SHA = sb.toString();
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".createHash(String str) =>" + "\n 발생원인 :" + e.getMessage());
			}
		} catch (NoSuchAlgorithmException e) {
			logger.info(this.getClass().getName() + ".createHash(String str) =>" + "\n 발생원인 :" + e.getMessage());
			SHA = null;
		}
		return SHA;
	}

	/*
	 * 
	 */
	private void runFile(String torrentFileName) {
		BufferedWriter oos = null;
		BufferedReader ois = null;

		HashMap<String, String> responseHM = new HashMap<String, String>();// key:parameter, value:content

		try {
			client = new Socket(client_ipAddress, Common.TrackerPort);

			oos = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF8"));
			ois = new BufferedReader(new InputStreamReader(client.getInputStream()));

			sendTrackerRequest(oos, torrentFileName);

			if (ois.readLine().equals(Common.TrackerResponseMessage)) {
				getTrackerResponse(ois, responseHM);
			}

			for (String str : swarm) {
				if ((str.split(":")[0]).equals(client_ipAddress)) {
					continue;
				}
				try {
					pt = new PeerThread(Integer.parseInt(str.split(":")[1]), info_hash, "Socket");
					Thread t = new Thread(pt);
					t.start();
				} catch (Exception e) {
					logger.info(this.getClass().getName() + ".runFile(): PeerThread error =>" 
							+ "\n 발생원인 :" + e.getMessage());
				}
			}

		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".runFile() => " + "\n 발생원인 :" + e.getMessage());
		} finally {
			try {
				if (ois != null) {
					ois.close();
				}
				if (oos != null) {
					oos.close();
				}
				if (client != null) {
					client.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".runFile(): close fail =>" + "\n 발생원인 :" + e.getMessage());
			}
		}
	}

	/*
	 * 파일 내부에 찾는 값이 존재하면 해당 값을 반환, 없으면 공백 반환
	 */
	private String searchValue(String torrentFileName, String searchValue) {
		FileReader fr = null;
		BufferedReader br = null;

		int pos = -1;
		String str = "";
		try {
			fr = new FileReader(Common.torrentFilePath + torrentFileName + ".txt");
			br = new BufferedReader(fr);
			while ((str = br.readLine()) != null) {
				if (str.split(":")[0].equals(searchValue)) {
					return str.split(":")[1];
				}
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".searchFile() =>" + "\n 발생원인 :" + e.getMessage());
		} finally {
			try {
				if (br != null) {
					br.close();
				}
				if (fr != null) {
					fr.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".runFile(): close fail =>" + "\n 발생원인 :" + e.getMessage());
			}
		}
		return str;
	}

	/*
	 * 
	 */
	private void sendTrackerRequest(BufferedWriter oos, String torrentFileName) {
		String str = "";

		long totalFileLength = -1;
		long currentFileLength = -1;
		String directory = "";
		String contentFileName = "";

		try {
			client = new Socket(client_ipAddress, Common.TrackerPort);
			oos.write(Common.TrackerRequestMessage + "\n");
			for (Common.TrackerRequest parameter : Common.TrackerRequest.values()) {
				switch (parameter) {
				case Info_Hash:
					if (!(str = searchValue(torrentFileName, Common.TorrentFileContents.Info_Hash.toString()))
							.isEmpty()) {
						oos.write(Common.TrackerRequest.Info_Hash.toString() + ":" + str + "\n");
						info_hash = str;
					}
					break;
				case Peer_Id:
					oos.write(Common.TrackerRequest.Peer_Id.toString() + ":" + client_user_id + "\n");
					break;
				case Port:
					oos.write(Common.TrackerRequest.Port.toString() + ":" + Common.TrackerPort + "\n");
					break;
				case Upload:
					break;
				case Download:
					break;
				case Left:
					if (!(str = searchValue(torrentFileName, Common.TorrentFileContents.FileLength.toString()))
							.isEmpty()) {
						totalFileLength = Long.parseLong(str);
					}
					if (!(str = searchValue(torrentFileName, Common.TorrentFileContents.Directory.toString()))
							.isEmpty()) {
						directory = str;
						if (findFile(new File(Common.downloadFilePath), directory) == -1) {
							oos.write(Common.TrackerRequest.Left.toString() + ":" + totalFileLength + "\n");
						}
					}
					if (!(str = searchValue(torrentFileName, Common.TorrentFileContents.ContentFileNames.toString()))
							.isEmpty()) {
						contentFileName = str;
						currentFileLength = findFile(new File(Common.downloadFilePath + directory),
								contentFileName);
						if (currentFileLength != -1) {
							oos.write(Common.TrackerRequest.Left.toString() + ":"
									+ (totalFileLength - currentFileLength) + "\n");
						}
					}
					break;
				case Key:
					break;
				case Event:
					if (currentFileLength == -1) {
						oos.write(Common.TrackerRequest.Event.toString() + ":Started\n");
					} else if (totalFileLength == currentFileLength) {
						oos.write(Common.TrackerRequest.Event.toString() + ":Completed\n");
					} else {
						oos.write(Common.TrackerRequest.Event.toString() + ":Downloading\n");
					}
					break;
				case Num_Want:
					break;
				case Compact:
					break;
				case No_Peer_Id:
					break;
				case IP:
					oos.write(Common.TrackerRequest.IP.toString() + ":" + client_ipAddress + ":" + clientServerPort
							+ "\n");
					break;
				}
			}
			oos.write(Common.EndMessage + "\n");
			oos.flush();

			int pieceLength = -1;
			if (!(str = searchValue(torrentFileName, Common.TorrentFileContents.PieceLength.toString())).isEmpty()) {
				pieceLength = Integer.parseInt(str);
			}
			makeInfoHashFile(info_hash, directory, contentFileName, pieceLength, totalFileLength);
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendRequest() =>" + "\n 발생원인 :" + e.getMessage());
		}
	}

	/*
	 * 
	 */
	private void getTrackerResponse(BufferedReader ois, HashMap<String, String> hashMap) {
		int pos = 0;
		String str = "";
		String peers = "";
		String[] peer;
		swarm = new ArrayList<String>();
		try {
			while ((str = ois.readLine()) != null) {
				if (str.equals(Common.EndMessage)) {
					break;
				}
				pos = str.indexOf(":");
				hashMap.put(str.substring(0, pos), str.substring(pos + 1));
			}

			peers = hashMap.get(Common.TrackerResponse.Peers.toString());
			peer = peers.split(",");
			for (String ip : peer) {
				swarm.add(ip);
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getTrackerResponse() =>" + "\n 발생원인 :" + e.getMessage());
		}

	}

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("USAGE : java Peer user_id ipAddress");
			System.exit(1);
		}
		new Peer(args[0], args[1]);
	}
}
