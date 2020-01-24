/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.IOException;
import java.rmi.server.ExportException;
import java.time.Duration;
import java.time.Instant;

class Encoder {

    //TreeWriter and dataWriter can be the same object. It just gives the option to save the tree and the data separately.
    static void Process(byte[][] input, BitStreamWriter treeWriter, BitStreamWriter dataWriter) throws IOException {
        int symbolIndex = 1;
        int endOfLineSymbol = symbolIndex++;
        long[] symbolReferences = new long[1 << 20];
        int[] symbolFrequencies = new int[1 << 20];
        int[] symbolSizes = new int[1 << 20];
        int[][] data = new int[input.length][];
        int[] lengths = new int[input.length];
        //The minCount can be used to have a speed/compression ratio trade off dial. MinCount should be at least 4 according to tests. 4 or 5 is optimal in most cases.
        int MinCount = 4;

        int totalLength = 0;

        Instant start = Instant.now();

        //First cycle
        //Convert bytes to symbols
        int[] references = new int[256];
        for (int i = 0; i < input.length; i++) {
            data[i] = new int[input[i].length + 1];
            for (int x = input[i].length - 1; x > -1; x--) {
                int b = input[i][x] & 0xFF;
                if (references[b] == 0) {
                    symbolReferences[symbolIndex] = b;
                    symbolSizes[symbolIndex] = 1;
                    references[b] = symbolIndex++;
                }
                data[i][x] = references[b];
            }
            totalLength+= data[i].length;
            data[i][input[i].length] = endOfLineSymbol;
            lengths[i] = data[i].length;
        }
        int startSymbols = symbolIndex;

        //Find the count for every combination of symbols that occur next to each other
        //The B+ Tree is surprising effective compared to hashing.
        //Symbol combinations are registered as a long, combining the 2 (32 bit) int symbols to one (64 bit) long.
        BPlusTreeLongInt set = new BPlusTreeLongInt();

        for (int i = 0; i < data.length; i++) {
            int s1 = data[i][0];
            int x = 1;
            symbolFrequencies[s1]++;
            while (x < lengths[i]) {
                int s = data[i][x];
                symbolFrequencies[s]++;
                if (s1 > 0 || (s != -s1)) {
                    long newNode = ((long) Math.abs(s1) << 32) + s;
                    set.addTo(newNode, 1);
                }
                if (s1 == s) {
                    s = -s;
                    data[i][x] = s;
                }
                s1 = s;

                x++;
            }
        }

        //Now we enter the main loop.
        //Jump out if the maximum number of symbols is reached.
        while(symbolIndex < symbolReferences.length) {

            //Remove the symbol combinations that will not be useful for further analysis/usage.
            //This is basically a cutoff point in a NP-problem.
            set.removeValuesBelow(MinCount);

            //Next phase is finding the combination of symbols that has the best properties to be fused together to form a new symbol.
            if(!set.prepareIteration())
                break;

            long bestNode = Long.MIN_VALUE;
            double bestScore = 0;
            int bestFrequency = 0;
            do {
                long node = set.iterationKey();
                int frequency = set.iterationValue();
                int symbol1 = (int) (node >> 32);
                int symbol2 = (int) (node);

                int symbol1Frequency = symbolFrequencies[symbol1];
                int symbol2Frequency = symbolFrequencies[symbol2];

                double ratio1 = (double) frequency / symbol1Frequency;
                double ratio2 = (double) frequency / symbol2Frequency;
                double max = Math.max(ratio1, ratio2);
                double min = Math.min(ratio1, ratio2);

                //A combination of frequency (count) in combination with the ratio of both parent symbols seems to lead to the best results.
                //The '- MinCount' part is to penalise low gains. The result is that the compressor recognises incompressible data and cuts of sooner.
                //The usages of the ratio's results in better compression because it helps the algorithm to steer in a direction where there are better future options of combining symbols.
                //It's like a gamble of the best direction in a breadth first search because traversing every possible path would require too much time.
                double score = frequency * (0.2 + (max + max + max + min)) - MinCount ;

                if (score > bestScore || (score >= bestScore && frequency > bestFrequency)) {
                    bestNode = node;
                    bestScore = score;
                    bestFrequency = frequency;
                }
            } while (set.nextIteration());

            //If there is no predicted gain: jump out of the loop.
            if(bestScore <= 0)
                break;

            //Now we have to fuse the 2 best symbols to a new one.
            int bestSymbol1 = (int)(bestNode >> 32);
            int bestSymbol2 = (int)(bestNode);

            int newSymbol = symbolIndex++;

            symbolReferences[newSymbol] = bestNode;
            //This loop does several things simultaneously.
            // 1. It fuses the 2 'best' symbols together to a new one.
            // 2. It removes the empty gaps left behind by the second symbol that is converted to a new one. (The new symbol is located on the first sumbol)
            // 3. It recounts the new situation.
            int div = 0;
            for (int i = 0; i < data.length; i++) {
                int moveTo = 0;
                int x = 0;
                int maxLength = lengths[i]-1;
                while(x < maxLength) {
                    int symbol = data[i][x++];
                    if (Math.abs(symbol) == bestSymbol1) {
                        int symbol2 = data[i][x];
                        if (Math.abs(symbol2) == bestSymbol2) {
                            boolean shouldInverse = false;
                            if (moveTo > 0) {
                                long oldSymbol = data[i][moveTo - 1];
                                if (oldSymbol == newSymbol)
                                    shouldInverse = true;
                                if (!(oldSymbol < 0 && -oldSymbol == symbol)) {
                                    set.addTo((Math.abs(oldSymbol) << 32) + bestSymbol1, -1);
                                }
                                if (!(oldSymbol < 0 && -oldSymbol == newSymbol)) {
                                    set.addTo((Math.abs(oldSymbol) << 32) + newSymbol, 1);
                                }
                            }
                            if (++x <= maxLength) {
                                long oldSymbol = data[i][x];

                                if (!(symbol2 < 0 && -symbol2 == oldSymbol)) {
                                    set.addTo(Math.abs(oldSymbol) + ((long) bestSymbol2 << 32), -1);
                                }
                                if(oldSymbol < 0)
                                    data[i][x] = -data[i][x];
                                set.addTo(Math.abs(oldSymbol) + ((long) newSymbol << 32), 1);
                            }
                            data[i][moveTo++] = shouldInverse ? -newSymbol : newSymbol;
                        } else {
                            data[i][moveTo++] = symbol;
                        }
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
            symbolFrequencies[newSymbol] += div;
            symbolSizes[newSymbol] = symbolSizes[bestSymbol1] + symbolSizes[bestSymbol2];

            totalLength -= div;

            //The best node is used and removed from the pool of options.
            set.delete(bestNode);

            //This part is to give some information about the process but only once every second.
            Instant current = Instant.now();
            if(Duration.between(start, current).getSeconds() > 0)
            {
                System.out.println(newSymbol + ": " + " best node: " + bestSymbol1 + "/" + bestSymbol2 + " gain: " + Math.round(bestScore) + " freq: " + bestFrequency + " left: " + totalLength);
                start = current;
            }
        }

        System.out.println("Symbols to encode:\t" + totalLength);

        //Convert the symbols and their parents to Huffman nodes.
        HuffmanNode[] nodes = new HuffmanNode[symbolIndex];
        for(int i = 1; i < symbolIndex; i++)
            nodes[i] = (i < startSymbols ? new HuffmanNode((int)symbolReferences[i], null, null) : new HuffmanNode(-2, nodes[(int)(symbolReferences[i]>>32)], nodes[(int)(symbolReferences[i])]));

        //Register the counts.
        for(int i = 0; i < data.length; i++)
            for(int x = 0; x < lengths[i]; x++)
                nodes[Math.abs(data[i][x])].frequency++;
        nodes[endOfLineSymbol].symbol = -1;

        //Stupid piece of code that should be removed. The nodes array starts from index 1. This code moves everything so things start at index 0.
        HuffmanNode[] forHuffman = new HuffmanNode[symbolIndex-1];
        System.arraycopy(nodes, 1, forHuffman, 0, symbolIndex - 1);

        //Lets make a canonical Huffman tree.
        CanonicalHuffmanTree tree = new CanonicalHuffmanTree(forHuffman);

        //And lastly write the data.
        //First the tree.
        tree.writeTree(treeWriter);

        //And now the nodes.
        for(int i = 0; i < data.length; i++)
            for(int x = 0; x < lengths[i]; x++)
                nodes[Math.abs(data[i][x])].bitSet.write(dataWriter);
    }


}
