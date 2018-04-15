import java.io.*;
import java.util.concurrent.BlockingQueue;

/**
 * This class takes chunks from the queue, writes them to disk and updates the
 * file's metadata.
 *
 * NOTE: make sure that the file interface you choose writes every update to the
 * file's content or metadata synchronously to the underlying storage device.
 */
public class FileWriter implements Runnable {
	private final BlockingQueue<Chunk> chunkQueue;
	private DownloadableMetadata downloadableMetadata;
	private File metaDataFile;
	private File tempFile;
	private final int CHUNK_SIZE = HTTPRangeGetter.CHUNK_SIZE;

	FileWriter(DownloadableMetadata downloadableMetadata, BlockingQueue<Chunk> chunkQueue) throws IOException {
		this.chunkQueue = chunkQueue;
		this.downloadableMetadata = downloadableMetadata;
		tempFile = File.createTempFile(downloadableMetadata.getFilename(), ".tmp",
				new File(System.getProperty("user.dir")));
	}

	private void writeChunks() throws IOException, InterruptedException {
		// declare the file
		RandomAccessFile file = new RandomAccessFile(downloadableMetadata.getFilename(), "rw");
		int previousPer = downloadableMetadata.getPercentage();
		Chunk chunk = null;

		// loop that write chunk from the queue, the condition is if the queue is not
		// empty and in the meta-data the process is not completed.
		while (true) {
			chunk = chunkQueue.take();

			if (chunk.getData() == null) {
				downloadableMetadata.setDownLoadIsCompleted();
				break;
			}

			long offset = chunk.getOffset();
			// seek to the right place in the file
			file.seek(offset);
			file.write(chunk.getData());
			downloadableMetadata.addRange((int) ((double) offset / CHUNK_SIZE));

			// serialize every change in percentage, for better performance.
			if (previousPer < downloadableMetadata.getPercentage()) {
				serialize();
				previousPer = printPercentage(previousPer);
			}
		}

		// close the random access file when we finish our download.
		file.close();
	}

	// we use the swapping algorithm to save current meta-data to disk.
	public void serialize() {
		try {
			// Saving an object in a file
			FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
			ObjectOutputStream out = new ObjectOutputStream(fileOutputStream);
			File temp = new File("temp");

			// Method for serialization of object
			out.writeObject(this.downloadableMetadata);
			out.close();
			fileOutputStream.close();

			try {
				if (temp.exists()) {
					try {
						if (metaDataFile.exists())
							temp.delete();
					} catch (Exception e) {
						temp.renameTo(this.metaDataFile);
					}
				}
			} catch (Exception e) {
			}

			try {
				if (metaDataFile.exists()) {
					metaDataFile.renameTo(temp);
					tempFile.renameTo(this.metaDataFile);
					temp.delete();
				}
			} catch (Exception e) {
				this.metaDataFile = new File(downloadableMetadata.getMetaDataFileName());
				tempFile.renameTo(this.metaDataFile);
			}
			// Files.move(tempFile.toPath(),
			// Paths.get(System.getProperty("user.dir").replace("tmp", "metadata")),
			// StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private int printPercentage(int i_PreviousPer) {
		int curPer = i_PreviousPer;

		if (downloadableMetadata.getPercentage() > i_PreviousPer) {
			if (downloadableMetadata.getPercentage() == 100) // avoid the printing of 100%.
			{
				downloadableMetadata.setDownLoadIsCompleted();
			} else {
				System.out.print("Downloaded ");
				System.out.print(downloadableMetadata.getPercentage());
				System.out.println("%");
				curPer = downloadableMetadata.getPercentage();
			}
		}

		return curPer;
	}

	@Override
	public void run() {
		try {
			this.writeChunks();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
