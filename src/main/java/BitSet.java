/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */


import java.io.IOException;

public class BitSet {

    long value;
    int length;
    public BitSet(int length)
    {
        this.length = length;
    }

    public BitSet(int length, long value)
    {
        this.length = length;
        this.value = value;
    }

    public BitSet(boolean bit)
    {
        this.length = 1;
        value = bit ? 1 : 0;
    }

    public BitSet(byte[] data)
    {
        this.length = data.length << 3;
        value = 0;
        for(int i = 0; i < length; i++)
            if((data[i>>3] >> (i & 7) & 1) == 1)
                value |= 1l << i;
    }

    public void increase()
    {
        value++;
    }

    public void increaseLength()
    {
        length++;
    }

    public void shiftRight()
    {
        value >>= 1;
    }

    public void shiftLeft()
    {
        value <<= 1;
    }

    public void set(int index)
    {
        value |= 1l << index;
    }

    public void or(BitSet other)
    {
        value |= other.value;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < length; i++)
        {
            sb.append(((value >> i) & 1));
        }
        return sb.toString();
    }

    public byte[] toByteArray()
    {
        byte[] result = new byte[(length >> 3) + ((length & 7) > 0 ? 1 : 0)];
        for(int i = 0; i < length; i++)
            if(((value >> i) & 1) > 0)
                result[i>>3] |= 1 << (i & 7);
        return result;
    }

    public int length()
    {
        return length;
    }

    public int write(byte[] buffer, int bufferOffset, int offset)
    {
        int toWrite = Math.min((buffer.length << 3) - bufferOffset, length - offset);
        for(int i = 0; i < toWrite; i++)
        {
            if(((value >> (i + offset)) & 1) > 0)
                buffer[bufferOffset >> 3] |= 1 << (bufferOffset & 7);
            bufferOffset++;
        }
        return toWrite;
    }

    public void write(BitStreamWriter writer) throws IOException {
        for(int i = 0; i < length; i++)
            writer.add(((value >> i) & 1) > 0);
    }

    public static BitSet read(BitStreamReader reader, int bits) throws Exception {
        long v = 0;
        for(int i = 0; i < bits; i++)
        {
            if(reader.nextBit() > 0)
                v |= 1l << i;
        }
        return new BitSet(bits, v);
    }

    public BitSet clone()
    {
        return new BitSet(length, value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BitSet other = (BitSet) obj;
        return other.length == length && other.value == value;
    }

    public BitSet reverse()
    {
        BitSet result = new BitSet(length);
        for(int i = 0; i < length; i++)
            if(((value >> i) & 1) == 1)
                result.value |= 1 << (length - i - 1);
        return result;
    }
}
