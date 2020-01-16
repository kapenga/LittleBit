/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.io.OutputStream;

class BitStreamWriter {
    private byte[] buffer;
    private final int bufferSize = 1 << 18;
    private int offset;

    private long length;

    private OutputStream stream;

    BitStreamWriter(OutputStream stream) {
        this.stream = stream;//new FileOutputStream(filename, false);
        buffer = new byte[bufferSize];
        offset = 0;
        length = 0;
    }

    void add(boolean bit) throws IOException {
        this.length++;
        if(bit)
            buffer[offset>>3] |= 1 << (offset & 7);
        offset++;
        if(offset >> 3 == bufferSize)
        {
            stream.write (buffer);
            offset = 0;
        }
    }


    private void flush() throws IOException {
        if(offset > 0)
            stream.write(buffer, 0,  (offset >> 3) + (((offset & 7) == 0) ? 0 : 1));
    }

    void close() throws IOException {
        flush();
    }

    long length()
    {
        return (length >> 3) + (((length & 7) == 0) ? 0 : 1);
    }
}
