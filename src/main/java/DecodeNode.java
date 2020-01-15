/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;

public class DecodeNode {

    private DecodeNode a;
    private DecodeNode b;

    public byte[] data;
    private BitSet bits;

    private DecodeNode toResolveA;
    private DecodeNode toResolveB;


    public DecodeNode(HashSet<BitSet> leaves, BitSet bits)
    {
        this.bits = bits.clone();

        BitSet newBits = bits.clone();
        newBits.increaseLength();
        newBits.shiftLeft();

        if(!leaves.contains(bits)) //is not a leave
        {
            a = new DecodeNode(leaves, newBits);
            newBits.increase();
            b = new DecodeNode(leaves, newBits);
        }
    }

    public DecodeNode get(BitStreamReader reader) throws Exception {
        if(a == null)//is leave
            return this;
        int bit = reader.nextBit();
        if(bit == 0)
            return a.get(reader);
        return b.get(reader);
    }

    public void addToDictionary(HashMap<BitSet, DecodeNode> dictionary)
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

    public void setData(byte[] data)
    {
        this.data = data;
    }

    public void setToResolveNodes(DecodeNode a, DecodeNode b)
    {
        this.toResolveA = a;
        this.toResolveB = b;
    }

    public void resolve()
    {
        if(data != null)
            return;
        toResolveA.resolve();
        toResolveB.resolve();
        data = combine(toResolveA.data, toResolveB.data);
    }


    public static byte[] combine(byte[] key1, byte[] key2)
    {
        byte[] key = new byte[key1.length + key2.length];
        for(int i = 0; i < key1.length; i++)
            key[i] = key1[i];
        for(int i = 0; i < key2.length; i++)
            key[key1.length + i] = key2[i];
        return key;
    }

    public void readField(BitStreamReader reader, FileOutputStream output) throws Exception {
        DecodeNode node = get(reader);
        while(node.data.length > 0)
        {
            output.write(node.data, 0, node.data.length);
            node = get(reader);
        }
    }

}
