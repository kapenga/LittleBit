/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;

class CanonicalHuffmanTree {

    private int[] depths;
    private HuffmanNode[][] frequencyArray;
    private HuffmanNode endOfLineSymbol;
    private int firstOccurrence;
    private int lastOccurrence;
    private int biggestCount;


    CanonicalHuffmanTree(HuffmanNode[] frequencies)
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

        for (HuffmanNode frequency : frequencies) {
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

    void writeTree(BitStreamWriter writer) throws IOException {
        long startLength = writer.length();

        int bitSize = DecodeNode.bitSize(biggestCount);
        BitSet bits = new BitSet(5, bitSize);
        BitSet last = new BitSet(6, lastOccurrence);
        int firstBitSize = DecodeNode.bitSize(lastOccurrence-1); //It should be smaller
        BitSet first = new BitSet(firstBitSize, firstOccurrence);
        writer.add(bits);
        writer.add(last);
        writer.add(first);

        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            BitSet amount = new BitSet(bitSize, depths[i]);
            writer.add(amount);
        }

        int maxLiteralCount = 257;
        writer.add(endOfLineSymbol.bitSet);  //end of line symbol first
        for(int i = firstOccurrence; i <= lastOccurrence; i++)
        {
            if(depths[i] > 0) {
                int literalCount = 0;
                for (int x = 0; x < depths[i]; x++) {
                    if (frequencyArray[i][x].a == null)
                        literalCount++;
                    else
                        break;
                }
                BitSet amount = new BitSet(Math.min(DecodeNode.bitSize(maxLiteralCount),DecodeNode.bitSize(depths[i])), literalCount);
                writer.add(amount);
                maxLiteralCount -= literalCount;
                for (int x = 0; x < literalCount; x++) {
                    if (frequencyArray[i][x].symbol > -1) {
                        writer.addByte(frequencyArray[i][x].symbol);
                    }
                }
                for (int x = literalCount; x < depths[i]; x++) {
                    writer.add(frequencyArray[i][x].a.bitSet);
                    writer.add(frequencyArray[i][x].b.bitSet);
                }
            }
        }
        long endLength = writer.length();
        System.out.println(" done.");

        System.out.println("Size of library:\t" + (endLength - startLength) + " bytes");
    }

}
