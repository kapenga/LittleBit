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
        long originHash = o.getHash();
        int index = (int) hash64(originHash);

        HashableLong other = values[index & mask];
        while (other != dummy && other != null && other.getHash() != originHash) {
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
        while (other == dummy || (other != null && other.getHash() != originHash)) {
            index++;
            other = values[index & mask];
        }
        return other;
    }

    void delete(HashableLong o) {
        long originHash = o.getHash();
        int index = (int)  hash64(originHash);

        HashableLong other = values[index & mask];
        while (other == dummy || (other != null && other.getHash() != originHash)) {
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
        a *= 0xc6a4a7935bd1e995L;
        return a ^ ((a*0xc6a4a7935bd1e995L) >>> 47);
    }
    private class DummyFiller implements HashableLong
    {
        @Override
        public long getHash() {
            return -1;
        }
    }
}
