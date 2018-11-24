package peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import common.Common;

public class PeerThread implements Runnable {
	enum HandShake {
		Info_Hash, Directory, FileName, PieceLength
	}

	enum Have {
		Piece_Index
	}

	enum Request {
		Piece_Index, Begin_Offset, Piece_Length
	}

	enum Piece {
		Piece_Index, Begin_Offset, Data_In_Piece, No_LastIndex, LastIndex_PieceLength
	}
	
	enum State {
		Peer, Seeder
	}

	static final String HandShakeMessage = "HandShake";

	static final String HaveMessage = "Have";

	static final String RequestMessage = "Request";

	static final String PieceMessage = "Piece";

	static final String NotHaveMessage = "Not Have";

	static final String EndMessage = "End";

	static ConcurrentHashMap<Integer, Integer> pieceInfo = new ConcurrentHashMap<Integer, Integer>();
	static String myState = "";
	static int completeFlag = 0;

	String peerState = "";

	Socket peer = null;

	String info_hash = "";
	String get_info_hash = "";

	String socketInstance = "";

	DataInputStream ois = null;
	DataOutputStream oos = null;

	String directory = "";
	String contentFileName = "";
	int pieceLength = -1;
	long fileLength = -1;
	int totalPieceFileNumber = 0;
	
	int getPieceIndex = -1;

	int sendFlag = 1;

	List<Integer> sendPieceIndex = new ArrayList<Integer>();

	Logger logger = Logger.getLogger(String.valueOf(this.getClass()));

	public PeerThread(int peerPort, String info_hash, String socketInstance) {
		this.info_hash = info_hash;
		this.socketInstance = socketInstance;
		try {
			peer = new Socket(Peer.client_ipAddress, peerPort);
		}catch (Exception e) {
			logger.info(this.getClass().getName() + ".PeerThread(): socket connect refuse =>" + "\n 발생원인:" + e.getMessage());
		}
		
		try {
			ois = new DataInputStream(peer.getInputStream());
			oos = new DataOutputStream(peer.getOutputStream());
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".PeerThread()=>" + "\n 발생원인:" + e.getMessage());
		}
	}
	
	public PeerThread(Socket peer, String info_hash, String socketInstance) {
		this.peer = peer;
		this.info_hash = info_hash;
		this.socketInstance = socketInstance;
		try {
			ois = new DataInputStream(peer.getInputStream());
			oos = new DataOutputStream(peer.getOutputStream());
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".PeerThread()=>" + "\n 발생원인:" + e.getMessage());
		}
	}

	public void run() {
		try {
			long start = System.currentTimeMillis();
			if (info_hash.isEmpty()) {
				if (ois.readUTF().equals(HandShakeMessage)) {
					getHandShake();
				}
				if (findFile(Common.infoHashFilePath + Peer.client_user_id + "/" + get_info_hash + ".txt")) {
					info_hash = get_info_hash;
					sendHandShake();
				}
			} else {
				sendHandShake();
				if (ois.readUTF().equals(HandShakeMessage)) {
					getHandShake();
				}
			}

			if (info_hash.equals(get_info_hash)) {

				setPeiceInfo();

				if (totalPieceFileNumber == pieceInfo.size()) {
					myState = State.Seeder.toString();
				} else {
					myState = State.Peer.toString();
				}

				oos.writeUTF(myState);
				oos.flush();
				peerState = ois.readUTF();

				String message = "";
				while (true) {
					if ((myState.equals(State.Seeder.toString()) && peerState.equals(State.Peer.toString()))
							|| (myState.equals(State.Peer.toString()) && peerState.equals(State.Seeder.toString()))) {
						if (((pieceInfo.size() > 0) && (sendPieceIndex.size() < pieceInfo.size())
								&& (totalPieceFileNumber == pieceInfo.size()))) {
							sendHave();
						}

						if (message.isEmpty()) {
							message = ois.readUTF();
						}

						if (message.equals(PieceMessage)) {
							getPiece();
							sendHave(getPieceIndex);
							message = "";
						} else if (message.equals(RequestMessage)) {
							if (getRequest()) {
								sendPiece();
								if ((message = ois.readUTF()).equals(HaveMessage)) {
									getHave();
									if (!pieceInfo.containsKey(getPieceIndex)) {
										sendRequest(getPieceIndex);
									}
									message = "";
								}
							}
						} else if (message.equals(HaveMessage)) {
							getHave();
							if (!pieceInfo.containsKey(getPieceIndex)) {
								sendRequest(getPieceIndex);
							} else {
								oos.writeUTF("");
								oos.flush();
							}
							message = "";
						}
					} else if ((myState.equals(State.Peer.toString()) && peerState.equals(State.Peer.toString()))) {
						if (((pieceInfo.size() > 0) && (sendPieceIndex.size() <= pieceInfo.size()))
								&& (sendFlag == 1)) {
							sendHave();
						}

						if (message.isEmpty()) {
							message = ois.readUTF();
						}

						if (message.equals(PieceMessage)) {
							getPiece();
							//sendHave(getPieceIndex);
							//sendFlag = 0;
							sendFlag = 1;
							message = "";
						} else if (message.equals(RequestMessage)) {
							if (getRequest()) {
								sendPiece();
								sendFlag = 0;
								if ((message = ois.readUTF()).equals(HaveMessage)) {
									getHave();
									if (!pieceInfo.containsKey(getPieceIndex)) {
										sendRequest(getPieceIndex);
										sendFlag = 0;
									} else {
										sendFlag = 1;
									}
									message = "";
								}
							}
						} else if (message.equals(HaveMessage)) {
							getHave();
							if (!pieceInfo.containsKey(getPieceIndex)) {
								sendRequest(getPieceIndex);
								sendFlag = 0;
							} else {
								sendFlag = 1;
							}
							message = "";
						} else if (message.equals(NotHaveMessage)) {
							message = "";
							sendFlag = 1;
						}
					}

					if ((totalPieceFileNumber == pieceInfo.size()) && (myState.equals(State.Peer.toString())) && (completeFlag == 0)) {
						combineFile();
						completeFlag = 1;
						long end = System.currentTimeMillis();
						System.out.println("torrent file download complete");
						System.out.println("다운로드 시간: " + (end - start) / 1000 + "초");
						removePieceFile();

						if (socketInstance.equals("Socket")) {
							break;
						}
					} else {
						System.out.println(
								"t:" + totalPieceFileNumber + "/p:" + pieceInfo.size() + "/s:" + sendPieceIndex.size());
						System.out.println("portNumber:" + peer.getPort());
						System.out.println("mystate:" + myState + "/peerstate:" + peerState);
					}
				}
				
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".run()=>" + "\n 발생원인:" + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	private boolean findFile(String filePath) {
		File file = new File(filePath);
		if (file.isFile()) {
			return true;
		} else {
			return false;
		}
	}

	/*
	 * 
	 */
	private void sendHandShake() {
		try {
			oos.writeUTF(HandShakeMessage);
			for (HandShake parameter : HandShake.values()) {
				switch (parameter) {
				case Info_Hash:
					oos.writeUTF(HandShake.Info_Hash.toString() + ":" + info_hash);
					break;
				}
			}
			oos.writeUTF(EndMessage);
			oos.flush();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendHandShake() => " + "\n 발생원인 : " + e.getMessage());
		}
	}

	/*
	 * 
	 */
	private void getHandShake() {
		String str = "";
		try {
			while (!(str = ois.readUTF()).equals(EndMessage)) {
				get_info_hash = str.split(":")[1];
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getHandShake() => " + "\n 발생원인 : " + e.getMessage());
		}
	}

	/*
	 * 
	 */
	private void sendHave() {
		
		
		List<Integer> pieceInfoKey = new ArrayList<Integer>();
		int pieceInfoKeyIndex = 0;

		try {
			for (int key : pieceInfo.keySet()) {
				pieceInfoKey.add(key);
			}

			if (sendPieceIndex.size() == pieceInfo.size()) {
				oos.writeUTF(NotHaveMessage);
				oos.flush();
				sendFlag = 0;
			} else {
				do {
					pieceInfoKeyIndex = (int) (Math.random() * (pieceInfoKey.size()));
				} while (sendPieceIndex.contains(pieceInfoKey.get(pieceInfoKeyIndex)));

				sendPieceIndex.add(pieceInfoKey.get(pieceInfoKeyIndex));

				oos.writeUTF(HaveMessage);
				oos.writeUTF(Have.Piece_Index.toString() + ":" + pieceInfoKey.get(pieceInfoKeyIndex));
				oos.writeUTF(EndMessage);
				oos.flush();

			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendHave() \n" + "pieceInfo_size: " + pieceInfo.size()
					+ "pieceInfoKeyIndex: " + pieceInfoKeyIndex + " => " + "\n 발생원인: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	private void sendHave(int pieceIndex) {
		try {
			oos.writeUTF(HaveMessage);
			oos.writeUTF(Have.Piece_Index.toString() + ":" + pieceIndex);
			oos.writeUTF(EndMessage);
			oos.flush();
			if (!sendPieceIndex.contains(pieceIndex)) {
				sendPieceIndex.add(pieceIndex);
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendHave(int pieceIndex) => " + "\n 발생원인: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	private void getHave() {
		String str = "";
		try {
			while (!(str = ois.readUTF()).equals(EndMessage)) {
				getPieceIndex = Integer.parseInt(str.split(":")[1]);
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getHave() => " + "\n 발생원인: " + e.getMessage());
		}
	}

	/*
	 * 
	 */
	private void sendRequest(int getPieceIndex) {
		try {
			oos.writeUTF(RequestMessage);
			oos.writeUTF(Request.Piece_Index.toString() + ":" + getPieceIndex);
			oos.writeUTF(EndMessage);
			oos.flush();
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendRequest() => " + "\n 발생원인: " + e.getMessage());
		}
	}

	/*
	 * 
	 */
	private boolean getRequest() {
		String str = "";
		boolean flag = false;
		try {
			while (!(str = ois.readUTF()).equals(EndMessage)) {
				getPieceIndex = Integer.parseInt(str.split(":")[1]);
				if (pieceInfo.containsKey(getPieceIndex)) {
					flag = true;
				}
			}
			return flag;
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getRequest() => " + "\n 발생원인: " + getPieceIndex + ":"
					+ e.getMessage());
			e.printStackTrace();
			return flag;
		}
	}

	/*
	 * 
	 */
	public void sendPiece() {
		File file = null;
		FileInputStream fi = null;
		BufferedInputStream bfi = null;
		byte[] readBuffer = null;

		try {
			file = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + contentFileName);
			if (file.isFile() && (myState.equals(State.Seeder.toString()))) {
				fi = new FileInputStream(file);
				bfi = new BufferedInputStream(fi);

				readBuffer = new byte[(int) Math.ceil(file.length())];
				bfi.read(readBuffer, 0, readBuffer.length);

				try {
					oos.writeUTF(PieceMessage);
					oos.writeUTF(Piece.Data_In_Piece.toString() + ":");
					oos.flush();
					if (getPieceIndex != totalPieceFileNumber) {
						oos.writeUTF(Piece.No_LastIndex.toString());
						oos.write(readBuffer, pieceInfo.get(getPieceIndex), pieceLength);
						oos.flush();
					} else {
						oos.writeUTF(Piece.LastIndex_PieceLength.toString() + ":"
								+ Integer.toString((int) Math.ceil(fileLength) - pieceInfo.get(getPieceIndex)));
						oos.write(readBuffer, pieceInfo.get(getPieceIndex),
								(int) Math.ceil(fileLength) - pieceInfo.get(getPieceIndex));
						oos.flush();
					}

					oos.writeUTF(Piece.Begin_Offset.toString() + ":" + pieceInfo.get(getPieceIndex));
					oos.writeUTF(EndMessage);
					oos.flush();
				} catch (Exception e) {
					logger.info(this.getClass().getName() + ".sendPiece()=>" + "\n 발생원인:" + e.getMessage());
					e.printStackTrace();
				}
			} else {
				file = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + contentFileName
						+ "_" + getPieceIndex);
				if (file.isFile()) {
					fi = new FileInputStream(file);
					bfi = new BufferedInputStream(fi);

					readBuffer = new byte[(int) Math.ceil(file.length())];
					bfi.read(readBuffer, 0, readBuffer.length);
					try {
						oos.writeUTF(PieceMessage);
						oos.writeUTF(Piece.Piece_Index.toString() + ":" + getPieceIndex);
						oos.writeUTF(Piece.Data_In_Piece.toString() + ":");
						oos.flush();
						if (getPieceIndex != totalPieceFileNumber) {
							oos.writeUTF(Piece.No_LastIndex.toString());
							oos.write(readBuffer, 0, pieceLength);
							oos.flush();
						} else {
							oos.writeUTF(Piece.LastIndex_PieceLength.toString() + ":" + file.length());
							oos.write(readBuffer, 0, (int) Math.ceil(file.length()));
							oos.flush();
						}

						oos.writeUTF(Piece.Begin_Offset.toString() + ":" + pieceInfo.get(getPieceIndex));
						oos.writeUTF(EndMessage);
						oos.flush();
					} catch (Exception e) {
						logger.info(this.getClass().getName() + ".sendPiece()=>" + "\n 발생원인:" + e.getMessage());
						e.printStackTrace();
					}
				}
			}

		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".sendPiece()=>" + "\n 발생원인:" + e.getMessage());
		} finally {
			try {
				if (bfi != null) {
					bfi.close();
				}
				if (fi != null) {
					fi.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".sendPiece()=>" + "\n 발생원인:" + e.getMessage());
			}
		}
	}

	/*
	 * 
	 */
	public void getPiece() {
		File filePiece = null;
		FileOutputStream fo = null;
		BufferedOutputStream bfo = null;
		String str = "";

		try {
			while (true) {
				str = ois.readUTF();
				if (str.split(":")[0].equals(Piece.Piece_Index.toString())) {
					getPieceIndex = Integer.parseInt(str.split(":")[1]);
				} else if (str.equals(Piece.Data_In_Piece.toString() + ":")) {

					byte[] readBuffer = new byte[pieceLength];

					if (getPieceIndex != totalPieceFileNumber) {
						ois.readUTF();
						ois.readFully(readBuffer, 0, pieceLength); 
					} else {
						String length = ois.readUTF().split(":")[1];
						ois.readFully(readBuffer, 0, Integer.parseInt(length));
					}

					try {
						try {
							File file = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory);
							if (!file.isFile()) {
								file.mkdirs();
							}
						} catch (Exception e) {
							logger.info(this.getClass().getName() + ".getPiece() => directory 생성 실패" + "\n 발생원인 :"
									+ e.getMessage());
						}

						filePiece = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory + "/"
								+ contentFileName + "_" + getPieceIndex);

						if (filePiece.isFile()) {
							continue;
						}

						fo = new FileOutputStream(filePiece);
						bfo = new BufferedOutputStream(fo);

						if (getPieceIndex != totalPieceFileNumber) {
							bfo.write(readBuffer);
						} else {
							for (int i = 0; readBuffer[i] != 0x00; i++) {
								bfo.write(readBuffer[i]);
							}
						}

						bfo.flush();

					} catch (Exception e) {
						logger.info(
								this.getClass().getName() + ".getPiece() => 파일 생성 실패 " + "\n 발생원인:" + e.getMessage());
					} finally {
						if (bfo != null) {
							bfo.close();
						}
						if (fo != null) {
							fo.close();
						}
					}
				} else if (str.split(":")[0].equals(Piece.Begin_Offset.toString())) {
					pieceInfo.put(getPieceIndex, Integer.parseInt(str.split(":")[1]));
				} else if (str.equals(EndMessage)) {
					break;
				}
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".getPiece() => " + "\n 발생원인:" + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	private void setPeiceInfo() {
		File searchDirectory = null;
		File searchFile = null;
		FileInputStream fi = null;
		BufferedInputStream bfi = null;

		getInfoHashContents();
		
		try {
			// search directory and file is exist
			searchDirectory = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory);
			if (searchDirectory.isDirectory()) {
				searchFile = new File(
						Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + contentFileName);
				// file is total file
				if (searchFile.isFile()) {
					fi = new FileInputStream(
							Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + contentFileName);
					fileLength = searchFile.length(); // seeder가 사용
					try {
						int currentReadLength = 0;
						int totalReadLength = 0;
						int pieceFileIndex = 0;
						bfi = new BufferedInputStream(fi);
						byte[] readBuffer = new byte[pieceLength];

						do {
							currentReadLength = bfi.read(readBuffer);
							if (currentReadLength == -1) {
								break;
							}
							totalReadLength += currentReadLength;

							pieceInfo.put(++pieceFileIndex, totalReadLength - currentReadLength);
						} while (true);

					} catch (Exception e) {
						logger.info(
								this.getClass().getName() + ".setPeiceIndexInfo() 구간3=>" + "\n 발생원인:" + e.getMessage());
					} finally {
						try {
							bfi.close();
						} catch (Exception e) {
							logger.info(this.getClass().getName() + ".setPeiceIndexInfo() 구간4=>" + "\n 발생원인:"
									+ e.getMessage());
						}
					}
				} else { // file is piece file
					File[] list = searchDirectory.listFiles();
					String index = "";
					for (File file : list) {
						index = file.getName().split("_")[1];
						pieceInfo.put(Integer.parseInt(index), Integer.parseInt(index) * pieceLength);
					}
				}
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".setPeiceIndexInfo() 구간5=>" + "\n 발생원인:" + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (fi != null) {
					fi.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".setPeiceIndexInfo 구간6()=>" + "\n 발생원인:" + e.getMessage());
			}

		}
	}
	
	/*
	 * 
	 */
	private void getInfoHashContents() {
		File infoHashFile = null;
		FileReader fr = null;
		BufferedReader br = null;

		try {
			infoHashFile = new File(Common.infoHashFilePath + Peer.client_user_id + "/" + info_hash + ".txt");
			fr = new FileReader(infoHashFile);
			br = new BufferedReader(fr);
			String str = "";
			while ((str = br.readLine()) != null) {
				for (Common.InfoHashFileContents parameter : Common.InfoHashFileContents.values()) {
					if(parameter.toString().equals(str.split(":")[0])) {
						switch (parameter) {
						case Directory:
							directory = str.split(":")[1];
							break;
						case ContentFileName:
							contentFileName = str.split(":")[1];
							break;
						case PieceLength:
							pieceLength = Integer.parseInt(str.split(":")[1]);
						case FileLength: 
							fileLength = Long.parseLong(str.split(":")[1]); // peer가 사용
							break;
						}
					}
				}
			}
			totalPieceFileNumber = (int) Math.ceil((double) fileLength / (double) pieceLength);
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".setPeiceIndexInfo() 구간1=>" + "\n 발생원인:" + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				br.close();
				fr.close();
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".setPeiceIndexInfo() 구간2=>" + "\n 발생원인:" + e.getMessage());

			}
		}
	}
	
	/*
	 * 
	 */
	private void combineFile() {
		File pieceFiles = null;
		FileOutputStream fo = null;
		FileInputStream fi = null;
		try {
			pieceFiles = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory);
			String[] files = pieceFiles.list();
			List<String> fileList = new ArrayList<String>();
			fileList = Arrays.asList(files);
			Collections.sort(fileList, new AscendingPieceIndex());

			if (fileList.size() != 1) {
				fo = new FileOutputStream(
						Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + contentFileName);
				for (int i = 0; i < fileList.size(); i++) {
					try {
						fi = new FileInputStream(
								Common.shareFilePath + Peer.client_user_id + "/" + directory + "/" + fileList.get(i));
						byte[] buf = new byte[pieceLength];
						int currentReadLength = 0;
						while ((currentReadLength = fi.read(buf)) > -1) {
							fo.write(buf, 0, currentReadLength);
						}
					} catch (Exception e) {
						logger.info(this.getClass().getName() + ".combineFile()=>" + "\n 발생원인:" + e.getMessage());
					} 
				}
				fo.flush();
			}
		} catch (Exception e) {
			logger.info(this.getClass().getName() + ".combineFile()=>" + "\n 발생원인:" + e.getMessage());
		} finally {
			try {
				if (fo != null) {
					fo.close();
				}
				if (fi != null) {
					fi.close();
				}
			} catch (Exception e) {
				logger.info(this.getClass().getName() + ".combineFile()=>" + "\n 발생원인:" + e.getMessage());
			}
		}
	}
	
	/*
	 * 
	 */
	private void removePieceFile() {
		File fileDirectory = new File(Common.shareFilePath + Peer.client_user_id + "/" + directory);
		File[] list = fileDirectory.listFiles();
		if (list != null) {
			for (File file : list) {
				if (file.getName().equals(contentFileName)) {
					continue;
				}else {
					file.delete();
				}
			}
		}
	}

	class AscendingPieceIndex implements Comparator<String> {

		@Override
		public int compare(String a, String b) {
			String[] tmp1 = a.split("_");
			String a_index = tmp1[1];

			String[] tmp2 = b.split("_");
			String b_index = tmp2[1];

			return Integer.parseInt(a_index) > Integer.parseInt(b_index) ? 1 : -1;
		}
	}

}
