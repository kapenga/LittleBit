/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)

DecodeNode is able to function without the encoder. This way it is possible to create a very small decoder.
 */

import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;

class DecodeNode {

    private DecodeNode a;
    private DecodeNode b;

    private byte[] data;
    private BitSet bits;

    private DecodeNode toResolveA;
    private DecodeNode toResolveB;


    private DecodeNode(HashSet<BitSet> leaves, BitSet bits)
    {
        this.bits = bits.copy();

        BitSet newBits = bits.copy();
        newBits.increaseLength();
        newBits.shiftLeft();

        if(!leaves.contains(bits)) //is not a leave
        {
            a = new DecodeNode(leaves, newBits);
            newBits.increase();
            b = new DecodeNode(leaves, newBits);
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

    private void addToDictionary(HashMap<BitSet, DecodeNode> dictionary)
    {
        if(a == null)//is leaf
        {
            dictionary.put(bits, this);
        }
        else
        {
            a.addToDictionary(dictionary);
            b.addToDictionary(dictionary);
        }
    }

    void setData(byte[] data)
    {
        this.data = data;
    }

    private void setToResolveNodes(DecodeNode a, DecodeNode b)
    {
        this.toResolveA = a;
        this.toResolveB = b;
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

    void readField(BitStreamReader reader, OutputStream output) throws Exception {
        DecodeNode node = get(reader);
        while(node.data.length > 0)
        {
            output.write(node.data, 0, node.data.length);
            node = get(reader);
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

    static DecodeNode readTree(BitStreamReader reader) throws Exception {
        int bitSize = (int)BitSet.read(reader, 5).getValue();
        int lastOccurrence = (int)BitSet.read(reader, 6).getValue();
        int firstBitSize = bitSize(lastOccurrence-1);
        int firstOccurrence = (int)BitSet.read(reader, firstBitSize).getValue();

        BitSet[][] huffbits = new BitSet[lastOccurrence+1][];
        int[] depths = new int[lastOccurrence+1];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            depths[i] =  (int)BitSet.read(reader, bitSize).getValue();
            huffbits[i] = new BitSet[depths[i]];
        }

        //Assign new canonical bits.
        BitSet bits = new BitSet(firstOccurrence,0);
        HashSet<BitSet> leafs = new HashSet<>();
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            for(int x = 0; x < depths[i]; x++)
            {
                huffbits[i][x] = bits.copy();
                leafs.add(huffbits[i][x]);
                bits.increase();
            }
            bits.increaseLength();
            bits.shiftLeft();
        }

        DecodeNode root = new DecodeNode(leafs, new BitSet(0));
        HashMap<BitSet, DecodeNode> dictionary = new HashMap<>();
        root.addToDictionary(dictionary);

        DecodeNode[][] nodes = new DecodeNode[lastOccurrence+1][];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            nodes[i] = new DecodeNode[depths[i]];
            for(int x = 0; x < depths[i]; x++)
                nodes[i][x] = dictionary.get(huffbits[i][x]);
        }

        DecodeNode endOfLineSymbol = root.get(reader);
        endOfLineSymbol.data = new byte[0];

        int maxLiteralCount = 257;
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            if(depths[i] > 0) {
                int literalCount = (int) BitSet.read(reader, Math.min(bitSize(maxLiteralCount), bitSize(depths[i]))).getValue();
                maxLiteralCount-=literalCount;
                for (int x = 0; x < literalCount; x++) {

                    if (nodes[i][x] != endOfLineSymbol) {
                        byte literalByte = (byte) BitSet.read(reader, 8).getValue();
                        nodes[i][x].setData(new byte[]{literalByte});
                    }
                }
                for (int x = literalCount; x < depths[i]; x++) {
                    nodes[i][x].setToResolveNodes(root.get(reader), root.get(reader));
                }
            }
        }

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            for(int x = 0; x < depths[i]; x++)
                nodes[i][x].resolve();

        return root;
    }

}
