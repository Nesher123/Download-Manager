
Chunk:
	A chunk of data file. Contains an offset, bytes of data, and size.

DownloadableMetadata:
	Describes a file's metadata: URL, file name, size, and which parts already downloaded to disk.
	
FileWriter:
	This class takes chunks from the queue, writes them to disk and updates the file's metadata.

HTTPRangeGetter:
	Each thread downloads a given URL. It reads CHUNK_SIZE at a time and writes it into a BlockingQueue and supports downloading a range of data, and limiting the download rate using a token bucket.

IdcDm:
	Receives arguments (URL to download, Maximum number of concurrent HTTP connections and Maximum download rate in bytes-per-second) from the command-line, provides some feedback and starts the download.

Range:
	Describes a simple range of bytes, with a start index, an end index, and a length.

RateLimiter:
	A token bucket based rate-limiter. Adds maxBytesPerSecond tokens to the bucket every second.
	
TokenBucket:
	We use the token bucket algorithm (https://en.wikipedia.org/wiki/Token_bucket) to enforce downloading a specific amount of bytes (i.e. tokens) per second.
