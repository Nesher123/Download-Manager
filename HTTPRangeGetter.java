import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;

/**
 * A runnable class which downloads a given url. It reads CHUNK_SIZE at a time
 * and writs it into a BlockingQueue. It supports downloading a range of data,
 * and limiting the download rate using a token bucket.
 */
public class HTTPRangeGetter implements Runnable {
	static final int CHUNK_SIZE = 4096;
	private static final int CONNECT_TIMEOUT = 500;
	private static final int READ_TIMEOUT = 2000;
	private final String url;
	private final Range range;
	private final BlockingQueue<Chunk> outQueue;
	private TokenBucket tokenBucket;

	HTTPRangeGetter(String url, Range range, BlockingQueue<Chunk> outQueue, TokenBucket tokenBucket) {
		this.url = url;
		this.range = range;
		this.outQueue = outQueue;
		this.tokenBucket = tokenBucket;
	}

	private void downloadRange() throws IOException, InterruptedException {
		// initialize a buffer to store the bytes we read from the stream.
		byte[] buffer = new byte[CHUNK_SIZE];

		try {
			// establish the connection
			HttpURLConnection httpUrlConnection = (HttpURLConnection) new URL(url).openConnection();
			httpUrlConnection.setRequestProperty("Accept-Encoding", "");
			httpUrlConnection.setRequestProperty("Range",
					"bytes=" + range.getStart().toString() + "-" + range.getEnd().toString());
			httpUrlConnection.setConnectTimeout(CONNECT_TIMEOUT);
			httpUrlConnection.setReadTimeout(READ_TIMEOUT);
			httpUrlConnection.connect();
			InputStream reader = httpUrlConnection.getInputStream();
			int lengthOfBytesWeRead;
			long offset = range.getStart();
			Chunk chunkWeAddToQueue;

			while (true) {
				try {
					// in order to allow the thread to start downloading, it needs to have
					// CHUNK_SIZE tokens available in the token bucket
					tokenBucket.take(CHUNK_SIZE);

					// recieve a data from the stream.
					lengthOfBytesWeRead = reader.read(buffer, 0, CHUNK_SIZE);

					if (lengthOfBytesWeRead == -1) {
						break;
					}

					if (offset > range.getEnd()) {
						break;
					}

					if (lengthOfBytesWeRead < CHUNK_SIZE) {
						byte[] realAmountThatWasReadBuffer = new byte[lengthOfBytesWeRead];
						System.arraycopy(buffer, 0, realAmountThatWasReadBuffer, 0, lengthOfBytesWeRead);

						chunkWeAddToQueue = new Chunk(realAmountThatWasReadBuffer, offset, lengthOfBytesWeRead);
					} else {
						chunkWeAddToQueue = new Chunk(buffer, offset, lengthOfBytesWeRead);
					}

					// pack into chunk.
					outQueue.add(chunkWeAddToQueue);

					// increase the offset.
					offset += lengthOfBytesWeRead;
				} catch (IOException ex) {
					System.out.println("Connection lost");
					Thread.sleep(3000); // sleep for 3 seconds before trying to reconnect
					break;
				} catch (Exception ex) {
					break;
				}
			}

			reader.close();
		} catch (Exception e) {
		}
	}

	@Override
	public void run() {
		try {
			this.downloadRange();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
