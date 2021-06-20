package encoders;/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import io.BitSet;

import java.util.ArrayList;

class HuffmanNode {
    int symbol;
    HuffmanNode a;
    HuffmanNode b;
    int frequency;
    int depth;

    BitSet bitSet;

    HuffmanNode(int symbol, HuffmanNode a, HuffmanNode b)
    {
        this.symbol = symbol;
        this.a = a;
        this.b = b;
        if(symbol > - 3 && this.a != null) {
            this.a.frequency++;
            this.b.frequency++;
        }
        frequency = 0;
    }

    static HuffmanNode method2(HuffmanNode[] inputs)
    {
        ArrayList<HuffmanNode> nodes = new ArrayList<>();
        BPlusTreeFSByteArray sortedList = new BPlusTreeFSByteArray(8);
        byte[] key = new byte[8];
        for(int i = 0; i < inputs.length; i++) {
            HuffmanNode node = inputs[i];
            nodes.add(node);
            BPlusTreeFSByteArray.write(key, node.frequency, 0);
            BPlusTreeFSByteArray.write(key, i, 4);
            sortedList.insert(key);
        }

        while(sortedList.size() > 1)
        {
            key = sortedList.removeFirst();
            int index = BPlusTreeFSByteArray.readInt(key, 4);
            //Pick the 2 smallest nodes
            HuffmanNode a = nodes.get(index);
            key = sortedList.removeFirst();
            index = BPlusTreeFSByteArray.readInt(key, 4);
            HuffmanNode b = nodes.get(index);
            HuffmanNode c = new HuffmanNode(-3, a, b);
            c.frequency = a.frequency + b.frequency;

            index = nodes.size();
            nodes.add(c);
            BPlusTreeFSByteArray.write(key, c.frequency, 0);
            BPlusTreeFSByteArray.write(key, index, 4);
            sortedList.insert(key);
        }


        key = sortedList.removeFirst();
        int index = BPlusTreeFSByteArray.readInt(key, 4);
        return nodes.get(index);
    }

    private boolean isLeaf()
    {
        return symbol > -3;
    }

    void getDepths(int[] depths, int currentDepth)
    {
        depth = currentDepth;
        if(isLeaf())
            depths[currentDepth]++;
        else
        {
            a.getDepths(depths, currentDepth+1);
            b.getDepths(depths, currentDepth+1);
        }
    }

    @Override
    public String toString()
    {
        return Integer.toString(symbol);
    }


}