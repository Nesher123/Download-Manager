/**
 * A token bucket based rate-limiter.
 *
 * This class should implement a "soft" rate limiter by adding maxBytesPerSecond
 * tokens to the bucket every second, or a "hard" rate limiter by resetting the
 * bucket to maxBytesPerSecond tokens every second.
 */
public class RateLimiter implements Runnable {
	private final TokenBucket tokenBucket;
	private Long maxBytesPerSecond;
	private final Long k_ConstatntSettingBaxBytesPerSecond = 1000000L;

	RateLimiter(TokenBucket tokenBucket, Long i_MaxBytesPerSecond) {
		this.tokenBucket = tokenBucket;
		this.maxBytesPerSecond = k_ConstatntSettingBaxBytesPerSecond;
		if (i_MaxBytesPerSecond != null) {
			this.maxBytesPerSecond = i_MaxBytesPerSecond;
		}
	}

	@Override
	/**
	 * Function: Run Description: will run when initialize a new RateLimiter thread.
	 * The Function will add constant (per program) amount of tokens to the tokenBucket.
	 * 
	 * Params: lastTimeWeMeasureTheClock - used to measure the last time we simple
	 * the clock.
	 * 
	 * Return: void
	 */
	public void run() {
		// enforce downloading a specific amount of bytes per seconds.
		long lastTimeWeMeasureTheClock = System.currentTimeMillis();

		while (true) {
			if (tokenBucket.terminated()) {
				break;
			}

			if (System.currentTimeMillis() - lastTimeWeMeasureTheClock >= 1000) // if a second had not passed
			{
				tokenBucket.add(maxBytesPerSecond);
				lastTimeWeMeasureTheClock = System.currentTimeMillis();
			}
		}
	}
}
