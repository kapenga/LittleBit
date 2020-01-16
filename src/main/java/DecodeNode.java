/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;

class DecodeNode {

    private DecodeNode a;
    private DecodeNode b;

    byte[] data;
    private BitSet bits;

    private DecodeNode toResolveA;
    private DecodeNode toResolveB;


    DecodeNode(HashSet<BitSet> leaves, BitSet bits)
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

    DecodeNode get(BitStreamReader reader) throws Exception {
        if(a == null)//is leave
            return this;
        int bit = reader.nextBit();
        if(bit == 0)
            return a.get(reader);
        return b.get(reader);
    }

    void addToDictionary(HashMap<BitSet, DecodeNode> dictionary)
    {
        if(a == null)//is leave
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

    void setToResolveNodes(DecodeNode a, DecodeNode b)
    {
        this.toResolveA = a;
        this.toResolveB = b;
    }

    void resolve()
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

    void readField(BitStreamReader reader, FileOutputStream output) throws Exception {
        DecodeNode node = get(reader);
        while(node.data.length > 0)
        {
            output.write(node.data, 0, node.data.length);
            node = get(reader);
        }
    }

}
