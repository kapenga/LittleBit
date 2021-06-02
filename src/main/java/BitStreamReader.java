/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.InputStream;

class BitStreamReader {

    private InputStream stream;
    private byte[] buffer;
    private int bufferLength;
    private int bitPosition;

    BitStreamReader(InputStream stream) {
        this.stream = stream;
        int bufferSize = 1 << 20;
        buffer = new byte[bufferSize];
        bufferLength = -1;
        bitPosition = 0;
    }

    int nextBit() throws Exception {
        if(bitPosition >= (bufferLength << 3))
        {
            bufferLength = stream.read (buffer);
            bitPosition = 0;
        }
        int result = (buffer[bitPosition >>> 3] >>> (bitPosition & 7)) & 1;
        bitPosition++;
        return result;
    }

}
