import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IdcDm {
	private final static int k_InitalizeSizeForTokenBucket = 0;
	private final static int CHUNK_SIZE = HTTPRangeGetter.CHUNK_SIZE;
	private static String currentDirectory = System.getProperty("user.dir");

	/**
	 * Receive arguments from the command-line, provide some feedback and start the
	 * download.
	 *
	 * @param args
	 *            command-line arguments
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		int numberOfWorkers = 1;
		Long maxBytesPerSecond = null;

		if (args.length < 1 || args.length > 3) {
			System.err.printf("usage:\n\tjava IdcDm URL [MAX-CONCURRENT-CONNECTIONS] [MAX-DOWNLOAD-LIMIT]\n");
			System.exit(1);
		} else if (args.length >= 2) {
			numberOfWorkers = Integer.parseInt(args[1]);
			if (args.length == 3)
				maxBytesPerSecond = Long.parseLong(args[2]);
		}

		String url = args[0];

		System.err.printf("Downloading");
		if (numberOfWorkers > 1)
			System.err.printf(" using %d connections", numberOfWorkers);
		if (maxBytesPerSecond != null)
			System.err.printf(" limited to %d Bps", maxBytesPerSecond);
		System.err.printf("...\n");

		try {
			DownloadURL(url, numberOfWorkers, maxBytesPerSecond);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initiate the file's metadata, and iterate over missing ranges. For each: 1.
	 * Setup the Queue, TokenBucket, DownloadableMetadata, FileWriter, RateLimiter,
	 * and a pool of HTTPRangeGetters 2. Join the HTTPRangeGetters, send finish
	 * marker to the Queue and terminate the TokenBucket 3. Join the FileWriter and
	 * RateLimiter
	 *
	 * Finally, print "Download succeeded/failed" and delete the metadata as needed.
	 *
	 * @param url
	 *            URL to download
	 * @param numberOfWorkers
	 *            number of concurrent connections
	 * @param maxBytesPerSecond
	 *            limit on download bytes-per-second
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static void DownloadURL(String url, int numberOfWorkers, Long maxBytesPerSecond)
			throws IOException, InterruptedException {
		// in order to determine the file's length, we open an HTTP connection and check
		// the header.
		HttpURLConnection fileSizeGetter = (HttpURLConnection) new URL(url).openConnection();
		long sizeOfFile = fileSizeGetter.getContentLength();
		fileSizeGetter.disconnect();

		// split into ranges according to the number of workers.
		long sizeOfSingleRange = ((long) (sizeOfFile / numberOfWorkers));
		int numberOfChunks = (int) Math.ceil((double) ((double) sizeOfFile / CHUNK_SIZE));
		DownloadableMetadata downloadableMetadata = null;

		if (DownloadableMetadata.checkIfMetaDataExists(url)) // check if metaData exists
		{
			downloadableMetadata = bringMetaDataBackToLife(url);
		} else {
			downloadableMetadata = new DownloadableMetadata(url, numberOfChunks);
		}

		/*
		 * 1. Setup the Queue, TokenBucket, DownloadableMetadata, FileWriter,
		 * RateLimiter
		 */
		BlockingQueue<Chunk> chunkQueue = new LinkedBlockingQueue<Chunk>();
		// declare the token bucket
		TokenBucket tokenBucket = new TokenBucket(k_InitalizeSizeForTokenBucket);
		// initialize the rate limiter
		Thread rateLimiter = new Thread(new RateLimiter(tokenBucket, maxBytesPerSecond));
		// initialize the file writer
		Thread fileWriter = new Thread(new FileWriter(downloadableMetadata, chunkQueue));
		// start the rate limiter and the filewriter.
		rateLimiter.start();
		fileWriter.start();
		// Initialize worker array, the array will manage the httpRangGetter according
		// to the number of the workers request by the user.
		Thread[] WorkersArray = new Thread[numberOfWorkers];

		boolean MissingRangesRemain = true;
		boolean[] MissingRangesArrayPerThread = new boolean[numberOfWorkers];
		Arrays.fill(MissingRangesArrayPerThread, true);

		while (MissingRangesRemain) {
			int i = 0;

			while (i < numberOfWorkers) {
				Range rangeForWorker = downloadableMetadata.getMissingRange(
						getRangeAccordingToNumberOfWorkers(i, sizeOfSingleRange, numberOfWorkers, sizeOfFile));

				// if there is missing range exists, then we will initialize a new worker.
				if (rangeForWorker != null) {
					Thread httpRangeGetter = new Thread(
							new HTTPRangeGetter(url, rangeForWorker, chunkQueue, tokenBucket));
					WorkersArray[i] = httpRangeGetter;
					httpRangeGetter.start();
				} else {
					MissingRangesArrayPerThread[i] = false;
				}

				i++;
			}

			/* 2. Join the HTTPRangeGetters */
			joinAllTheWorkers(WorkersArray);

			// wait for the queue.
			waitUntilTheQueueIsEmpty(chunkQueue);

			MissingRangesRemain = checkIfAllTheRangesWasDownloaded(numberOfWorkers, MissingRangesArrayPerThread);
		}

		/* send finish marker to the Queue and terminate the TokenBucket */
		Chunk markFininshChunk = new Chunk(null, -1, -1);
		chunkQueue.add(markFininshChunk);

		tokenBucket.terminate();

		/* 3. Join the FileWriter and RateLimiter */
		try {
			rateLimiter.join();
			fileWriter.join();
		} catch (InterruptedException e) {
			System.err.println("Problem when trying to join the RateLimiter and the FileWriter.");
			e.printStackTrace();
		}

		/*
		 * Finally, print "Download succeeded/failed" and delete the metadata as needed.
		 */
		if (downloadableMetadata.isCompleted()) {
			System.out.println("Download succeeded");
			downloadableMetadata.delete();
			deleteTemporaries();
		} else {
			System.out.println("Download failed");
		}
	}

	private static Range getRangeAccordingToNumberOfWorkers(int i, long i_SizeOfSingleRange, int i_NumberOfWorkers,
			long i_SizeOfFile) {
		int startRange;
		int endRange;

		if (i > 0) {
			startRange = (int) Math.ceil(((double) ((double) i * i_SizeOfSingleRange / CHUNK_SIZE)));
		} else {
			startRange = (int) Math.ceil(((double) ((double) i * i_SizeOfSingleRange / CHUNK_SIZE)));
		}

		startRange *= CHUNK_SIZE;

		if (i != i_NumberOfWorkers - 1) {
			endRange = (int) Math.ceil((double) ((double) (i + 1) * i_SizeOfSingleRange / CHUNK_SIZE));
			endRange *= CHUNK_SIZE;
			endRange -= 1;
		} else {
			endRange = (int) (i_SizeOfFile - 1);
		}

		Range returnRange = new Range((long) startRange, (long) endRange);

		return returnRange;
	}

	private static void joinAllTheWorkers(Thread[] workersThreads) throws InterruptedException {
		for (Thread worker : workersThreads) {
			if (worker != null) {
				worker.join();
			}
		}
	}

	public static DownloadableMetadata bringMetaDataBackToLife(String url) {
		String metaDataFileName = DownloadableMetadata.getMetadataName(DownloadableMetadata.getName(url));
		DownloadableMetadata metaDataObject = null;

		try {
			FileInputStream file = new FileInputStream(metaDataFileName);
			ObjectInputStream input = new ObjectInputStream(file);
			metaDataObject = (DownloadableMetadata) input.readObject();
			file.close();
			input.close();
		} catch (Exception ex) {
			System.err.println("Problem occure when trying to bring the meta-data back to the progrem.");
		}

		return metaDataObject;

	}

	static void waitUntilTheQueueIsEmpty(BlockingQueue<Chunk> chunkQueue) throws InterruptedException {
		while (!chunkQueue.isEmpty()) {
			Thread.sleep(1000);
		}
	}

	private static boolean checkIfAllTheRangesWasDownloaded(int i_NumberOfWorkers,
			boolean[] i_MissingRangesArrayPerThread) {

		boolean MissingRangesRemain = true;
		for (int i = 0; i < i_NumberOfWorkers; i++) {
			if (i_MissingRangesArrayPerThread[i] == true) {
				break;
			}
			// If we got until here, no more missing ranges remian.
			if (i == i_NumberOfWorkers - 1) {
				MissingRangesRemain = false;
			}
		}

		return MissingRangesRemain;
	}

	// find all temp files and delete them when download is finished.
	private static void deleteTemporaries() {
		File[] matchingFiles = new File(currentDirectory).listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("tmp");
			}
		});

		for (File file : matchingFiles) {
			file.delete();
		}
	}
}
