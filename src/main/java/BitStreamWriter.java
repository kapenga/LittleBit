/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

class BitStreamWriter {
    private byte[] buffer;
    private final int bufferSize = 1 << 24;
    private int offset;

    private long length;

    private OutputStream stream;

    BitStreamWriter(OutputStream stream) {
        this.stream = stream;
        buffer = new byte[bufferSize];
        offset = 0;
        length = 0;
    }

    void add(BitSet bits) throws IOException {
        long v = bits.getValue();
        int length = bits.getLength();

        while(length > 0) {
            buffer[offset >> 3] |= v << (offset & 7);
            int inc = Math.min(8 - (offset & 7), length);
            offset += inc;
            length -= inc;
            this.length += inc;
            v >>= inc;
            if (offset == (bufferSize << 3)) {
                stream.write(buffer);
                Arrays.fill(buffer, (byte) 0);
                offset = 0;
            }
        }
    }

    void addByte(int v) throws IOException {
        int length = 8;

        while(length > 0) {
            buffer[offset >> 3] |= v << (offset & 7);
            int inc = Math.min(8 - (offset & 7), length);
            offset += inc;
            length -= inc;
            this.length += inc;
            v >>= inc;
            if (offset == (bufferSize << 3)) {
                stream.write(buffer);
                Arrays.fill(buffer, (byte) 0);
                offset = 0;
            }
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
