/**
 * A Token Bucket (https://en.wikipedia.org/wiki/Token_bucket)
 *
 * This thread-safe bucket should support the following methods:
 * - take(n): remove n tokens from the bucket (blocks until n tokens are available and taken)
 * - set(n): set the bucket to contain n tokens (to allow "hard" rate limiting)
 * - add(n): add n tokens to the bucket (to allow "soft" rate limiting)
 * - terminate(): mark the bucket as terminated (used to communicate between threads)
 * - terminated(): return true if the bucket is terminated, false otherwise
 */
class TokenBucket {
	private long m_CurNumOfTokens;
	private boolean k_BucketIsTerminated;

	TokenBucket(long tokens) {
		m_CurNumOfTokens = tokens;
		k_BucketIsTerminated = false;
	}

	synchronized void take(long tokens) {
		while (true) {
			if (m_CurNumOfTokens >= tokens) {
				m_CurNumOfTokens -= tokens;
				break;
			} else {
				try {
					Thread.sleep(100); // Send this thread to sleep for 100 milliseconds for a second to pass
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	void add(long tokens) {
		m_CurNumOfTokens += tokens;
	}

	synchronized void terminate() {
		k_BucketIsTerminated = true;
	}

	boolean terminated() {
		return k_BucketIsTerminated;
	}
}
