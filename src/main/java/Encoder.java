/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class Encoder {

    public static void Process(byte[][] input, BitStreamWriter writer) throws IOException {
        int symbolIndex = 1;
        int endOfLineSymbol = symbolIndex++;
        long[] symbolReferences = new long[1 << 20];
        int[] symbolFrequencies = new int[1 << 20];
        int[][] data = new int[input.length][];
        int[] lengths = new int[input.length];
        final int MinCount = 4;
        int totalLength = 0;

        Instant start = Instant.now();


        //First cycle
        int[] references = new int[256];
        for (int i = 0; i < input.length; i++) {
            data[i] = new int[input[i].length + 1];
            for (int x = input[i].length - 1; x > -1; x--) {
                int b = input[i][x] & 0xFF;
                if (references[b] == 0) {
                    symbolReferences[symbolIndex] = b;
                    references[b] = symbolIndex++;
                }
                data[i][x] = references[b];
            }
            totalLength+= data[i].length;
            data[i][input[i].length] = endOfLineSymbol;
            lengths[i] = data[i].length;
        }
        int startSymbols = symbolIndex;

        BPlusTreeLongInt set = new BPlusTreeLongInt();

        for (int i = 0; i < input.length; i++) {
            int s2 = 0;
            int s1 = data[i][0];
            int x = 1;
            symbolFrequencies[s1]++;
            while (x < lengths[i]) {
                int s = data[i][x];
                symbolFrequencies[s]++;
                if (s == s1 && s == s2) {
                    s2 = 0;
                } else {
                    long newNode = ((long) s1 << 32) + s;
                    set.addTo(newNode, 1);
                    s2 = s1;
                    s1 = s;
                }
                x++;
            }
        }
        while(true) {
            set.removeValuesBelow(MinCount);

            if(!set.prepareIteration())
                break;

            long bestNode = Long.MIN_VALUE;
            double bestGain = 0;
            int bestFrequency = 0;
            while(true)
            {
                long node = set.iterationKey();
                int frequency = set.iterationValue();
                int symbol1 = (int) (node >> 32);
                int symbol2 = (int) (node);

                int symbol1Frequency = symbolFrequencies[symbol1];
                int symbol2Frequency = symbolFrequencies[symbol2];

                double normalisedSymbol1Frequency = (double) frequency / symbol1Frequency;
                double normalisedSymbol2Frequency = (double) frequency / symbol2Frequency;
                double max = Math.max(normalisedSymbol1Frequency, normalisedSymbol2Frequency);
                double min = Math.min(normalisedSymbol1Frequency, normalisedSymbol2Frequency);
                double gain = (frequency * ((max * 3) + min)) - 0.2 -  (1.0 - min) - (1.0 - max);

                if (gain > bestGain || (gain >= bestGain && frequency > bestFrequency)) {
                    bestNode = node;
                    bestGain = gain;
                    bestFrequency = frequency;
                }
                if(!set.nextIteration())
                    break;
            }

            if(bestGain <= 0)
                break;

            int bestSymbol1 = (int)(bestNode >> 32);
            int bestSymbol2 = (int)(bestNode);
            symbolReferences[symbolIndex] = bestNode;
            symbolFrequencies[symbolIndex] += bestFrequency;

            int newSymbol = symbolIndex++;

            int div = 0;
            for (int i = 0; i < data.length; i++) {
                int moveTo = 0;
                int x = 0;
                int maxLength = lengths[i]-1;
                while(x < maxLength) {
                    int symbol = data[i][x++];
                    if (symbol == bestSymbol1 && data[i][x] == bestSymbol2) {
                        if(moveTo > 0)
                        {
                            long oldSymbol = data[i][moveTo-1];
                            oldSymbol <<= 32;
                            set.addTo(oldSymbol + symbol, -1);
                            set.addTo(oldSymbol + newSymbol, 1);
                        }
                        if(++x <= maxLength)
                        {
                            long oldSymbol =  data[i][x];
                            set.addTo(oldSymbol + ((long) bestSymbol2 << 32), -1);
                            set.addTo(oldSymbol + ((long) newSymbol << 32), 1);
                        }
                        data[i][moveTo++] = newSymbol;
                    } else {
                        data[i][moveTo++] = symbol;
                    }
                }
                if(x <= maxLength) {
                    data[i][moveTo++] = data[i][x];
                }
                div += lengths[i] - moveTo;
                lengths[i] = moveTo;
            }
            symbolFrequencies[bestSymbol1] -= div;
            symbolFrequencies[bestSymbol2] -= div;
            totalLength -= div;

            set.delete(bestNode);

            Instant current = Instant.now();
            if(Duration.between(start, current).getSeconds() > 0) {
                System.out.println(symbolIndex + ": " + " best node: " + bestSymbol1 + "/" + bestSymbol2 + " gain: " + Math.round(bestGain) + " freq: " + bestFrequency + " left: " + totalLength);
                start = current;
            }
        }

        System.out.println("Number of symbols to encode: " + totalLength);

        HuffmanNode[] nodes = new HuffmanNode[symbolIndex];
        for(int i = 1; i < symbolIndex; i++)
            nodes[i] = (i < startSymbols ? new HuffmanNode((int)symbolReferences[i], null, null) : new HuffmanNode(-2, nodes[(int)(symbolReferences[i]>>32)], nodes[(int)(symbolReferences[i])]));

        for(int i = 0; i < data.length; i++)
            for(int x = 0; x < lengths[i]; x++)
                nodes[data[i][x]].frequency++;
        nodes[endOfLineSymbol].symbol = -1;

        HuffmanNode[] forHuffman = new HuffmanNode[symbolIndex-1];
        for(int i = 0; i < symbolIndex-1; i++)
            forHuffman[i] = nodes[i+1];

        CanonicalHuffmanTree tree = new CanonicalHuffmanTree(forHuffman);

        tree.writeTree(writer);
        for(int i = 0; i < data.length; i++)
            for(int x = 0; x < lengths[i]; x++)
                nodes[data[i][x]].bitSet.write(writer);
    }

}
