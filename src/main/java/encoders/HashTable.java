package encoders;

public class HashTable {

    private final HashableLong[] values;
    private final int length;
    private final int mask;
    private final DummyFiller dummy;

    HashTable(int bitSize) {
        dummy = new DummyFiller();
        this.length = 1 << bitSize;
        this.values = new HashableLong[length];
        this.mask = length - 1;
    }

    void add(HashableLong o) {
        long originHash = o.hashValue;
        int index = (int)  hash64(originHash);

        HashableLong other = values[index & mask];
        while (other != dummy && other != null && other.hashValue != originHash) {
            index++;
            other = values[index & mask];
        }

        if (other != null && other != dummy)
            return;
        values[index & mask] = o;
    }

    HashableLong get(long originHash) {
        int index = (int) hash64(originHash);

        HashableLong other = values[index & mask];
        while (other == dummy || (other != null && other.hashValue != originHash)) {
            index++;
            other = values[index & mask];
        }
        return other;
    }

    void delete(HashableLong o) {
        long originHash = o.hashValue;
        int index = (int)  hash64(originHash);

        HashableLong other = values[index & mask];
        while (other == dummy || (other != null && other.hashValue != originHash)) {
            index++;
            other = values[index & mask];
        }

        if (other == null)
            return;

        values[index & mask] = dummy;
    }

    //Small shortened hash alg for a long value, -> MurmurHash64 made naked.
    //Don't know its good or not. Seems to work.
    static long hash64(long a)
    {
        //return interleave(a);
        a *= 0xc6a4a7935bd1e995L;
        return a ^ (a >>> 47);// ((a*0xc6a4a7935bd1e995L) >>> 47);
    }

    public static long interleave(long x) {
        return (spaceOut(x >> 32) << 1) | spaceOut(x);
    }

    private static long spaceOut(long x) {
        x = x          & 0x00000000FFFFFFFFL;
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x <<  8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x <<  4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x <<  2)) & 0x3333333333333333L;
        x = (x | (x <<  1)) & 0x5555555555555555L;
        return x;
    }

    private class DummyFiller extends HashableLong
    {
        public DummyFiller()
        {
            hashValue = -1;
        }
    }
}
