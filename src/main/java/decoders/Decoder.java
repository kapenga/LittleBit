/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)

Decoder & BitStreamReader can be used without the other classes to create a small decoder library.
 */
package decoders;

import io.BitStreamReader;
import java.io.InputStream;
import java.io.OutputStream;

public class Decoder {
    private DecodeNode root;

    public Decoder(BitStreamReader inputStream) throws Exception {
        root = readTree(inputStream);
    }

    public Decoder(InputStream inputStream) throws Exception {
        root = readTree(inputStream);
    }

    public void readField(BitStreamReader reader, OutputStream output) throws Exception {
        DecodeNode node = root.get(reader);
        while(true)
        {
            output.write(node.data, 0, node.data.length);
            if(node.hasEndOfLine)
                break;
            node = root.get(reader);
        }
    }

    private DecodeNode readTree(InputStream reader) throws Exception {
        return readTree(new BitStreamReader(reader));
    }

    private DecodeNode readTree(BitStreamReader reader) throws Exception {
        int bitSize = reader.nextBits(5);
        int lastOccurrence = reader.nextBits(6);
        int firstBitSize = bitSize(lastOccurrence-1);
        int firstOccurrence = reader.nextBits(firstBitSize);

        int[] depths = new int[lastOccurrence+1];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            depths[i] =  reader.nextBits(bitSize);

        DecodeNode[][] nodes = new DecodeNode[lastOccurrence+1][];
        int[] leaveCount = new int[depths.length];

        DecodeNode root = new DecodeNode(leaveCount, depths, lastOccurrence, nodes, 0);
        root.setLeaves(0, new DecodeNode[0], new int[1], 0);

        int maxLiteralCount = 257;
        int[] literalCounts = new int[lastOccurrence+1];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            if(depths[i] > 0) {
                int literalCount = reader.nextBits(Math.min(bitSize(maxLiteralCount), bitSize(depths[i])));
                literalCounts[i] = literalCount;
                maxLiteralCount-=literalCount;
                for (int x = 0; x < literalCount; x++) {

                        byte literalByte = (byte)reader.nextBits(8);
                        if(literalByte == 0)
                        {
                            if(reader.nextBit() == 1)
                                nodes[i][x].data = new byte[]{literalByte};
                            else
                            {
                                nodes[i][x].data = new byte[0];
                                nodes[i][x].hasEndOfLine = true;
                            }
                        }
                        else
                            nodes[i][x].data = new byte[]{literalByte};
                }
                for (int x = literalCount; x < depths[i]; x++) {
                    nodes[i][x].toResolveA = root.get(reader);
                    nodes[i][x].toResolveB = root.get(reader);
                }
            }
        }

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            for(int x = literalCounts[i]; x < depths[i]; x++)
                nodes[i][x].resolve();
        return root;
    }

    private static int reverse(int i, int n)
    {
        i = (i & 0x55555555) <<  1 | (i & 0xaaaaaaaa) >>>  1;
        i = (i & 0x33333333) <<  2 | (i & 0xcccccccc) >>>  2;
        i = (i & 0x0f0f0f0f) <<  4 | (i & 0xf0f0f0f0) >>>  4;
        i = (i & 0x00ff00ff) <<  8 | (i & 0xff00ff00) >>>  8;
        return (i << 16 | i >>> 16) >>> (32 - n);
    }

    public static int bitSize(long i)
    {
        int result = 0;
        while(i > 0)
        {
            result++;
            i>>>=1;
        }
        return result;
    }

    class DecodeNode {
        DecodeNode a;
        DecodeNode b;

        byte[] data;

        DecodeNode toResolveA;
        DecodeNode toResolveB;

        DecodeNode[] leaves;
        int leaveBits;

        boolean hasEndOfLine;

        int firstLeaveDepth;
        int depth;

        DecodeNode(int[] leaveCount, int[] depths, int topDepth, DecodeNode[][] nodes, int depth)
        {
            this.depth = depth;

            if(leaveCount[depth] < depths[depth])
            {
                if(nodes[depth]==null)
                    nodes[depth] = new DecodeNode[depths[depth]];
                nodes[depth][leaveCount[depth]] = this;
                leaveCount[depth]++;

                firstLeaveDepth = depth;
            }
            else //is not a leave
            {
                a = new DecodeNode(leaveCount, depths, topDepth, nodes, depth + 1);
                b = new DecodeNode(leaveCount, depths, topDepth, nodes, depth + 1);
                firstLeaveDepth = Math.min(a.firstLeaveDepth, b.firstLeaveDepth);
            }
        }

        void setLeaves(int nextDepth, DecodeNode[] leavesToSet, int[] leaveIndex, int bitSize)
        {
            if(depth == nextDepth)
            {
                if(leavesToSet.length > 0)
                    leavesToSet[reverse(leaveIndex[0]++, bitSize)] = this;
                if(a != null)
                {
                    leaveBits = firstLeaveDepth-depth;
                    leaves = new DecodeNode[1 << leaveBits];

                    int[] leaveIndex2 = new int[1];
                    a.setLeaves(firstLeaveDepth, leaves, leaveIndex2, leaveBits);
                    b.setLeaves(firstLeaveDepth, leaves, leaveIndex2, leaveBits);
                }
            }
            else {
                a.setLeaves(nextDepth, leavesToSet, leaveIndex, bitSize);
                b.setLeaves(nextDepth, leavesToSet, leaveIndex, bitSize);
            }
        }

        DecodeNode get(BitStreamReader reader) throws Exception {
            DecodeNode n = this;
            while(n.leaves != null)//is leaf
                n = n.leaves[reader.nextBits(n.leaveBits)];
            return n;
        }

        void resolve()
        {
            if(data != null)
                return;
            toResolveA.resolve();
            toResolveB.resolve();
            data = combine(toResolveA.data, toResolveB.data);
            hasEndOfLine = toResolveB.hasEndOfLine;
            toResolveA = null;
            toResolveB = null;
            a = null;
            b = null;
        }
    }

    private static byte[] combine(byte[] key1, byte[] key2)
    {
        byte[] key = new byte[key1.length + key2.length];
        System.arraycopy(key1, 0, key, 0, key1.length);
        System.arraycopy(key2, 0, key, key1.length, key2.length);
        return key;
    }
}
