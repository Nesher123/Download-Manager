import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Describes a file's metadata: URL, file name, size, and which parts already
 * downloaded to disk.
 *
 * The metadata (or at least which parts already downloaded to disk) is
 * constantly stored safely in disk. When constructing a new metadata object, we
 * first check the disk to load existing metadata.
 *
 * CHALLENGE: try to avoid metadata disk footprint of O(n) in the average case
 * HINT: avoid the obvious bitmap solution, and think about ranges...
 */
@SuppressWarnings("serial")
class DownloadableMetadata implements Serializable {
	private final String metadataFilename;
	private String filename;
	private String url;
	private boolean k_downloadIsCompleted = false;
	private boolean[] m_MissingChunks;
	private int m_NumberOfChunks;
	public int m_NumberOfChunksTheDownloaded;
	private final int CHUNK_SIZE = HTTPRangeGetter.CHUNK_SIZE;

	DownloadableMetadata(String url, int numberOfChunks) {
		this.url = url;
		this.filename = getName(url);
		this.metadataFilename = getMetadataName(filename);
		this.m_NumberOfChunks = numberOfChunks;
		this.m_NumberOfChunksTheDownloaded = 0;
		this.creatMissingChunksArray();
	}

	public String getMetaDataFileName() {
		return this.metadataFilename;
	}

	public static String getMetadataName(String filename) {
		return filename + ".metadata";
	}

	public static String getName(String path) {
		return path.substring(path.lastIndexOf('/') + 1, path.length());
	}

	void addRange(int chunkId) {
		// check whether that the first time we read that specific chunk ID.
		if (m_MissingChunks[chunkId] != false) {
			m_MissingChunks[chunkId] = false;
			m_NumberOfChunksTheDownloaded++;
		}
	}

	public int getPercentage() {
		// we multiply by 100 for getting the percentage downloaded
		return (int) (((double) m_NumberOfChunksTheDownloaded / m_NumberOfChunks) * 100);
	}

	String getFilename() {
		return filename;
	}

	boolean isCompleted() {
		return k_downloadIsCompleted;
	}

	void setDownLoadIsCompleted() {
		k_downloadIsCompleted = true;
	}

	private void creatMissingChunksArray() {
		m_MissingChunks = new boolean[m_NumberOfChunks];
		// Initialized the array with zeros = not downloaded yet.
		Arrays.fill(m_MissingChunks, true);
	}

	void delete() {
		try {
			File metadataFile = new File(getMetadataName(filename));
			metadataFile.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean checkIfMetaDataExists(String url) {
		File metaDataFile = new File(getMetadataName(getName(url)));

		return metaDataFile.exists();
	}

	Range getMissingRange(Range givenRange) {
		Range returnRange = null;
		int i = (int) Math.floor((double) givenRange.getStart() / CHUNK_SIZE);
		int topLimitRange = (int) Math.floor((double) givenRange.getEnd() / CHUNK_SIZE);

		while (i <= topLimitRange) {
			if (returnRange == null) {
				if (m_MissingChunks[i] == true) // when we counter the first chunk index that we didn't download yet
				{
					returnRange = new Range((long) Math.floor((double) i * CHUNK_SIZE), (long) -1);
				}
			} else {
				if (m_MissingChunks[i] == false) // if that is our first download chunk after we set the start range.
				{
					returnRange = new Range(returnRange.getStart(), (long) Math.ceil((double) (i - 1) * CHUNK_SIZE));
				}
			}

			i++;
		}

		if ((returnRange != null) && (returnRange.getEnd() < 0)) // incase that we need to bring even the last chunk
		{
			returnRange = new Range(returnRange.getStart(), givenRange.getEnd());
		}

		return returnRange;
	}

	String getUrl() {
		return url;
	}
}
