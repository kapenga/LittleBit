/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class BitStreamReader {

    private FileInputStream stream;
    private byte[] buffer;
    private final int bufferSize = 1 << 16;
    private int bufferLength;
    private int bitPosition;

    public BitStreamReader(String filename) throws FileNotFoundException {
        this.stream = new FileInputStream(filename);
        buffer = new byte[bufferSize];
        bufferLength = -1;
        bitPosition = 0;
    }

    public int nextBit() throws Exception {
        if(bitPosition >= (bufferLength << 3))
        {
            bufferLength = stream.read (buffer);
            bitPosition = 0;
        }
        int result = (buffer[bitPosition >>> 3] >>> (bitPosition & 7)) & 1;
        bitPosition++;
        return result;
    }


    public void close() throws IOException {
        stream.close();
    }

}
