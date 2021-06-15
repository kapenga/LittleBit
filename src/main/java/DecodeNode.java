/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)

DecodeNode is able to function without the encoder. This way it is possible to create a very small decoder.
 */

import java.io.OutputStream;

class DecodeNode {

    private DecodeNode a;
    private DecodeNode b;

    private byte[] data;

    private DecodeNode toResolveA;
    private DecodeNode toResolveB;

    private int depth;

    private DecodeNode(int[] leaveCount, int[] depths, int topDepth,  int[] topIndex, DecodeNode[] top, DecodeNode[][] nodes, int depth)
    {
        this.depth = depth;

        if(leaveCount[depth] < depths[depth])
        {
            if(nodes[depth]==null)
                nodes[depth] = new DecodeNode[depths[depth]];
            nodes[depth][leaveCount[depth]] = this;
            leaveCount[depth]++;

            int topNodeSize = 1 << (topDepth - depth);
            for(int i = 0; i < topNodeSize; i++)
                top[topIndex[0]++] = this;
        }
        else //is not a leave
        {
            a = new DecodeNode(leaveCount, depths, topDepth, topIndex, top, nodes, depth + 1);
            b = new DecodeNode(leaveCount, depths, topDepth, topIndex, top, nodes, depth + 1);
        }
    }

    private DecodeNode get(BitStreamReader reader) throws Exception {
        DecodeNode n = this;
        while(n.a != null) {//is leaf
            int bit = reader.nextBit();
            if (bit == 0)
                n = n.a;
            else
                n = n.b;
        }
        return n;
    }

    private void resolve()
    {
        if(data != null)
            return;
        toResolveA.resolve();
        toResolveB.resolve();
        data = combine(toResolveA.data, toResolveB.data);
    }

    private static byte[] combine(byte[] key1, byte[] key2)
    {
        byte[] key = new byte[key1.length + key2.length];
        System.arraycopy(key1, 0, key, 0, key1.length);
        System.arraycopy(key2, 0, key, key1.length, key2.length);
        return key;
    }

    static void readFields(DecodeNode[] tops, BitStreamReader reader, OutputStream output) throws Exception {
        int bitSize = bitSize(tops.length-1);
        int mask = (1 << bitSize)-1;
        int index = Integer.reverse(reader.nextBits(bitSize)) >>> (32 - bitSize);

        DecodeNode node = tops[index].get(reader);
        while(node.data.length > 0)
        {
            output.write(node.data, 0, node.data.length);
            index = (Integer.reverse(reader.nextBits(node.depth)) >>> (32 - node.depth)) | (index << node.depth);
            node = tops[index&mask].get(reader);
        }
    }

    static int bitSize(long i)
    {
        int result = 0;
        while(i > 0)
        {
            result++;
            i>>>=1;
        }
        return result;
    }

    static DecodeNode[] readTree(BitStreamReader reader) throws Exception {
        int bitSize = reader.nextBits(5);
        int lastOccurrence = reader.nextBits(6);
        int firstBitSize = bitSize(lastOccurrence-1);
        int firstOccurrence = reader.nextBits(firstBitSize);

        int[] depths = new int[lastOccurrence+1];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            depths[i] =  reader.nextBits(bitSize);

        DecodeNode[] top = new DecodeNode[1 << lastOccurrence];
        int[] topIndex = new int[1];
        DecodeNode[][] nodes = new DecodeNode[lastOccurrence+1][];
        int[] leaveCount = new int[depths.length];

        DecodeNode root = new DecodeNode(leaveCount, depths, lastOccurrence, topIndex, top, nodes, 0);

        DecodeNode endOfLineSymbol = root.get(reader);
        endOfLineSymbol.data = new byte[0];

        int maxLiteralCount = 257;
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            if(depths[i] > 0) {
                int literalCount = reader.nextBits(Math.min(bitSize(maxLiteralCount), bitSize(depths[i])));
                maxLiteralCount-=literalCount;
                for (int x = 0; x < literalCount; x++) {

                    if (nodes[i][x] != endOfLineSymbol) {
                        byte literalByte = (byte)reader.nextBits(8);
                        nodes[i][x].data = new byte[]{literalByte};
                    }
                }
                for (int x = literalCount; x < depths[i]; x++) {
                    nodes[i][x].toResolveA = root.get(reader);
                    nodes[i][x].toResolveB = root.get(reader);
                }
            }
        }

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            for(int x = 0; x < depths[i]; x++)
                nodes[i][x].resolve();

        return top;
    }
}
