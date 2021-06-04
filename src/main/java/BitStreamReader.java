/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.io.InputStream;

class BitStreamReader {

    private InputStream stream;
    private byte[] buffer;
    private int bufferLength;
    private int bytePosition;
    private int cache;
    private int cacheLeft;

    BitStreamReader(InputStream stream) {
        this.stream = stream;
        int bufferSize = 1 << 20;
        buffer = new byte[bufferSize];
        bufferLength = 0;
        bytePosition = 0;
    }

    int nextBit() throws Exception {
        if(cacheLeft < 1) {
            while (cacheLeft < 24) {
                if (bytePosition >= bufferLength) {
                    bufferLength = stream.read(buffer);
                    bytePosition = 0;
                }
                cache |= (buffer[bytePosition++] & 0xFF) << cacheLeft;
                cacheLeft += 8;
            }
        }
        int result = (cache & 1);
        cache >>= 1;
        cacheLeft--;
        return result;
    }

    int nextBits(int length) throws IOException {
        if(cacheLeft < length) {
            while (cacheLeft < 24) {
                if (bytePosition >= bufferLength) {
                    bufferLength = stream.read(buffer);
                    bytePosition = 0;
                }
                cache |= (buffer[bytePosition++] & 0xFF) << cacheLeft;
                cacheLeft += 8;
            }
        }
        int result = (cache & ((1 << length) - 1));
        cache >>= length;
        cacheLeft-=length;
        return result;
    }


}
