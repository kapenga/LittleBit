/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public class CanonicalHuffmanTree {

    private int[] depths;
    private HuffmanNode[][] frequencyArray;
    private HuffmanNode endOfLineSymbol;
    private int firstOccurrence;
    private int lastOccurrence;
    private int biggestCount;


    public CanonicalHuffmanTree(HuffmanNode[] frequencies)
    {
        endOfLineSymbol = frequencies[0];
        HuffmanNode root = HuffmanNode.method2(frequencies);

        int[] depths = new int[64];
        root.getDepths(depths, 0);

        this.depths = depths;
        firstOccurrence = 0;
        lastOccurrence = 0;
        biggestCount = 0;

        for(int i = 0; i < depths.length; i++)
        {
            if(depths[i] > 0) {
                if(depths[i] > biggestCount)
                    biggestCount = depths[i];
                if(firstOccurrence == 0)
                    firstOccurrence = i;
                lastOccurrence = i;
            }
        }

        HuffmanNode[][] newFreqs = new HuffmanNode[64][];
        for(int i = 0; i < newFreqs.length; i++)
            newFreqs[i] = new HuffmanNode[depths[i]];

        for(int i = 0; i < frequencies.length; i++)
        {
            HuffmanNode frequency = frequencies[i];
            int index = frequency.depth;
            newFreqs[index][--depths[index]] = frequency;
        }

        //Restore the depths values
        for(int i = 0; i < newFreqs.length; i++)
            depths[i] = newFreqs[i].length;

        //Move the literals to the front
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            int literalIndex = 0;

            for(int x = 0; x < newFreqs[i].length; x++)
            {
                if(newFreqs[i][x].a == null) //Literal
                {
                    if(x > literalIndex)
                    {
                        HuffmanNode temp = newFreqs[i][literalIndex];
                        newFreqs[i][literalIndex] = newFreqs[i][x];
                        newFreqs[i][x] = temp;
                    }
                    literalIndex++;
                }
            }
        }

        //Assign new canonical bits.
        BitSet bits = new BitSet(firstOccurrence,0);
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            for(int x = 0; x < newFreqs[i].length; x++)
            {
                newFreqs[i][x].bitSet = bits.reverse();
                bits.increase();
            }
            bits.increaseLength();
            bits.shiftLeft();
        }
        this.frequencyArray = newFreqs;
    }

    private int bitSize(long i)
    {
        return (int)Math.ceil((Math.log(i) / Math.log(2)));
    }

    public void writeTree(BitStreamWriter writer) throws IOException {
        long startLength = writer.length();

        int bitSize = bitSize(biggestCount);
        BitSet bits = new BitSet(5, bitSize);
        BitSet first = new BitSet(6, firstOccurrence);
        BitSet last = new BitSet(6, lastOccurrence);
        bits.write(writer);
        first.write(writer);
        last.write(writer);

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            BitSet amount = new BitSet(bitSize, depths[i]);
            amount.write(writer);
        }

        endOfLineSymbol.bitSet.write(writer); //end of line symbol first
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            int literalCount = 0;
            for(int x = 0; x < depths[i]; x++)
            {
                if(frequencyArray[i][x].a == null)
                    literalCount++;
                else
                    break;
            }
            BitSet amount = new BitSet(bitSize, literalCount);
            amount.write(writer);
            for(int x = 0; x < literalCount; x++)
            {
                if(frequencyArray[i][x].symbol > -1) {
                    BitSet literalByte = new BitSet(8, frequencyArray[i][x].symbol);
                    literalByte.write(writer);
                }
            }
            for(int x = literalCount; x < depths[i]; x++)
            {
                frequencyArray[i][x].a.bitSet.write(writer);
                frequencyArray[i][x].b.bitSet.write(writer);
            }
        }
        long endLength = writer.length();
        System.out.println("Size of library: " + (endLength - startLength) + " bits. That's " + ((endLength - startLength) >> 3) + " bytes.");
    }

    public static DecodeNode readTree(BitStreamReader reader) throws Exception {
        int bitSize = (int)BitSet.read(reader, 5).value;
        int firstOccurrence = (int)BitSet.read(reader, 6).value;
        int lastOccurrence = (int)BitSet.read(reader, 6).value;

        BitSet[][] huffbits = new BitSet[lastOccurrence+1][];
        int[] depths = new int[lastOccurrence+1];
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            depths[i] =  (int)BitSet.read(reader, bitSize).value;
            huffbits[i] = new BitSet[depths[i]];
        }

        //Assign new canonical bits.
        BitSet bits = new BitSet(firstOccurrence,0);
        HashSet<BitSet> leafs = new HashSet<>();
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            for(int x = 0; x < depths[i]; x++)
            {
                huffbits[i][x] = bits.clone();
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

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            int literalCount = (int)BitSet.read(reader, bitSize).value;
            for(int x = 0; x < literalCount; x++) {

                if(nodes[i][x] != endOfLineSymbol)
                {
                    byte literalByte = (byte) BitSet.read(reader, 8).value;
                    nodes[i][x].setData(new byte[]{literalByte});
                }
            }
            for(int x = literalCount; x < depths[i]; x++)
            {
                nodes[i][x].setToResolveNodes(root.get(reader), root.get(reader));
            }
        }

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
            for(int x = 0; x < depths[i]; x++)
                nodes[i][x].resolve();

        return root;
    }

}
