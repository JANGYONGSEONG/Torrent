package common;

/*
 *  peer와 tracker가 주고 받는 message 이름 및 message 항목, 토렌트 파일 항목, directory 경로
 */
public class Common {

	public enum InformCreateFile {
		Info_Hash, IP
	}

	public enum TrackerRequest {
		Info_Hash, Peer_Id, Port, Upload, Download, Left, Key, Event, Num_Want, Compact, No_Peer_Id, IP
	}

	public enum TrackerResponse {
		Complete, Downloaded, Incomplete, Interval, MinInterval, Peers
	}

	public enum TorrentFileContents {
		TorrentFileName, TrackerURL, Directory, CreatedOn, CreatedBy, Comment, ContentFileNames, FileLength,
		PieceLength, Private, Info_Hash
	}

	public enum InfoHashFileContents {
		Directory, ContentFileName, PieceLength, FileLength
	}


	public static final String swarmFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/swarmFile/";

	public static final String peerServerPortFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/peerServerPortFile/";

	public static final String torrentFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/torrentFile/";

	public static final String infoHashFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/infoHashFile/";

	public static final String shareFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/shareFile/";

	public static final String downloadFilePath = "/home/yongs/eclipse-workspace/torrent/src/fileDirectory/downloadFile";
	

	public static final String TrackerRequestMessage = "TrackerRequest";

	public static final String TrackerResponseMessage = "TrackerResponse";

	public static final String InformCreateFileMessage = "InformCreateFile";

	public static final String EndMessage = "End";
	
	public static final int TrackerPort = 7000;

}
