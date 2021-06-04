/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

class BitSet {

    private long value;
    private int length;

    BitSet(int length)
    {
        this.length = length;
    }

    BitSet(int length, long value)
    {
        this.length = length;
        this.value = value;
    }

    void increase()
    {
        value++;
    }

    void increaseLength()
    {
        length++;
    }

    void shiftLeft()
    {
        value <<= 1;
    }

    int getLength()
    {
        return length;
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

    long getValue()
    {
        return value;
    }

    static BitSet read(BitStreamReader reader, int bits) throws Exception {
//        long v = 0;
//        for(int i = 0; i < bits; i++)
//        {
//            if(reader.nextBit() > 0)
//                v |= 1L << i;
//        }
//        return new BitSet(bits, v);
        return new BitSet(bits, reader.nextBits(bits));
    }

    BitSet copy()
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

    BitSet reverse()
    {
        BitSet result = new BitSet(length);
        for(int i = 0; i < length; i++)
            if(((value >> i) & 1) == 1)
                result.value |= 1 << (length - i - 1);
        return result;
    }
}
