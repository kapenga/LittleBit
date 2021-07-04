package encoders;

import decoders.Decoder;
import io.BitStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class Encoder {
    private final static int endOfLineSymbol = 0;
    private int symbolIndex = 1;
    private int[] symbolReferenceA;
    private int[] symbolReferenceB;
    private int[] symbolCount;
    private int[] symbolSize;
    private boolean[] hasEndSymbol;

    private int[] symbols;

    private int[] offsetsLeft;
    private int[] offsetsRight;
    private HashTable fromSymbolToSymbol;
    private BPlusTreeFSByteArray scoreList;
    private ArrayList<CNode> touched;

    private final static int MINIMALCOUNT = 4;
    private final int MAXSYMBOLCOUNT;

    private Encoder(int maxSymbolCount)
    {
        MAXSYMBOLCOUNT = Math.min(257 + maxSymbolCount, 1 << 24);
        symbolReferenceA = new int[MAXSYMBOLCOUNT];
        symbolReferenceB = new int[MAXSYMBOLCOUNT];
        symbolCount = new int[MAXSYMBOLCOUNT];
        symbolSize = new int[MAXSYMBOLCOUNT];
        hasEndSymbol = new boolean[MAXSYMBOLCOUNT];
    }

    private void symbolize(int[] symbols) {
        /*
            1. Find most common symbol pair

            2. Make it a new symbol
            3. Repeat
         */
        fromSymbolToSymbol = new HashTable(Math.max(20, Decoder.bitSize(symbols.length)-4));
        scoreList = new BPlusTreeFSByteArray(12);
        touched = new ArrayList<>();
        this.symbols = symbols;

        offsetsLeft = new int[symbols.length];
        offsetsRight = new int[symbols.length];

        for (int i = 0; i < offsetsLeft.length; i++) {
            offsetsLeft[i] = -1;
            offsetsRight[i] = -1;
        }

        long lastSymbol = -1;
        for (int i = 0; i < symbols.length - 1; i++) {
            symbolCount[symbols[i]]++;
            long currentSymbol = ((long) symbols[i] << 32) + symbols[i + 1];

            addSymbol(currentSymbol, i, currentSymbol != lastSymbol);

            lastSymbol = (currentSymbol == lastSymbol) ? -1 : currentSymbol;
        }
        symbolCount[symbols[symbols.length - 1]]++;

        while (true) {
            processTouched();

            if (scoreList.size() < 1 || symbolIndex >= MAXSYMBOLCOUNT) {
                System.out.println(" done.");
                System.out.println("Number of symbols:\t" + symbolIndex);
                break;
            }

            byte[] lastElement = scoreList.removeLast();
            long key = BPlusTreeFSByteArray.readLong(lastElement, 4);

            CNode winner = (CNode)fromSymbolToSymbol.get(key);

            int symbolA = (int)(winner.hashValue >> 32);
            int symbolB = (int)winner.hashValue;
            symbolReferenceA[symbolIndex] = symbolA;
            symbolReferenceB[symbolIndex] = symbolB;
            symbolSize[symbolIndex] = symbolSize[symbolA] + symbolSize[symbolB];
            hasEndSymbol[symbolIndex] = hasEndSymbol[symbolB];
            int newSymbol = symbolIndex++;
            fromSymbolToSymbol.delete(winner);

            int nextOffset = symbolSize[symbolA];

            int index = winner.firstIndex;
            long lastTriggerSymbol = -1;
            while (index > -1) {
                int futureIndex = offsetsRight[index];
                if (symbols[index] > -1) {

                    int previousIndex = index;
                    while(--previousIndex > -1 && symbols[previousIndex] < 0);

                    if (previousIndex > -1) {
                        lastSymbol = (((long) symbols[previousIndex]) << 32) + symbols[index];
                        removeSymbol(lastSymbol, previousIndex);
                    }

                    int nextIndex = index + nextOffset;

                    int nextNextIndex = nextIndex;
                    while(++nextNextIndex < symbols.length && symbols[nextNextIndex] < 0);

                    if (nextNextIndex < symbols.length) {
                        lastSymbol = (((long) symbolB) << 32) + symbols[nextNextIndex];

                        if (lastSymbol != key)
                            removeSymbol(lastSymbol, nextIndex);
                    }

                    winner.remove(index);
                    symbolCount[symbolA]--;
                    symbolCount[symbolB]--;
                    symbolCount[newSymbol]++;
                    symbols[index] = newSymbol;
                    symbols[nextIndex] = -1;

                    if (previousIndex > -1) {
                        int prevSymbol = symbols[previousIndex];
                        long previousSymbol = (((long) symbols[previousIndex]) << 32) + newSymbol;
                        if(!hasEndSymbol[prevSymbol] && symbolCount[prevSymbol] >= MINIMALCOUNT)
                            addSymbol(previousSymbol, previousIndex, previousSymbol != lastTriggerSymbol);

                        lastTriggerSymbol = (previousSymbol != lastTriggerSymbol && symbols[previousIndex] == newSymbol) ? previousSymbol : -1;
                    }

                    if (nextNextIndex < symbols.length) {
                        int nextSymbol = symbols[nextNextIndex];

                        if(!hasEndSymbol[newSymbol] && symbolCount[nextSymbol] >= MINIMALCOUNT)
                            addSymbol((((long)newSymbol) << 32) + nextSymbol, index, true);
                    }
                }

                index = futureIndex;
            }

        }

        offsetsLeft = null;
        offsetsRight = null;
        fromSymbolToSymbol = null;
        scoreList = null;
        touched = null;
    }

    private void writeHuffman(BitStreamWriter treeWriter, BitStreamWriter dataWriter, long[] rowPositions) throws IOException {
        //Convert the symbols and their parents to Huffman nodes.
        HuffmanNode[] nodes = new HuffmanNode[symbolIndex];
        for(int i = 0; i < symbolIndex; i++)
            nodes[i] = (symbolReferenceB[i] < 0 ? new HuffmanNode(symbolReferenceA[i], null, null) : new HuffmanNode(-2, nodes[symbolReferenceA[i]], nodes[symbolReferenceB[i]]));

        //Register the counts.
        for (int symbol : symbols)
            if (symbol > -1)
                nodes[symbol].frequency++;

        //Lets make a canonical Huffman tree.
        CanonicalHuffmanTree tree = new CanonicalHuffmanTree(nodes);

        //And lastly write the data.
        //First the tree.
        tree.writeTree(treeWriter);

        long dataWriterStart = dataWriter.length();
        int rowPositionIndex = 0;
        rowPositions[rowPositionIndex++] = 0;
        //And now the nodes.
        for (int symbol : symbols)
            if (symbol > -1) {
                dataWriter.add(nodes[symbol].bitSet);
                if(hasEndSymbol[symbol] && rowPositions.length > rowPositionIndex)
                    rowPositions[rowPositionIndex++] = dataWriter.bitLength();
            }

        System.out.println("Size of data:\t\t" + (dataWriter.length() - dataWriterStart) + " bytes");
    }

    private void addSymbol(long symbol, int index, boolean countMe)
    {
        CNode node = (CNode)fromSymbolToSymbol.get(symbol);
        if(node == null)
        {
            node = new CNode(symbol, index);
            fromSymbolToSymbol.add(node);
            touched.add(node);
        }
        else
        {
            if(!node.touched) {
                touched.add(node);
                node.touched = true;
            }
            node.add(index);
        }
        if(countMe) {
            node.change++;
        }
    }

    private void removeSymbol(long symbol, int index)
    {
        CNode node = (CNode) fromSymbolToSymbol.get(symbol);
        if(node != null) {
            if(!node.touched) {
                touched.add(node);
                node.touched = true;
            }
            node.change--;
            node.remove(index);
        }
    }

    private void processTouched()
    {
        byte[] key = new byte[12];
        for(CNode node : touched)
        {
            BPlusTreeFSByteArray.write(key, node.count, 0);
            BPlusTreeFSByteArray.write(key, node.hashValue, 4);

            scoreList.delete(key);

            node.count += node.change;
            node.change = 0;
            node.touched = false;

            if(node.count >= MINIMALCOUNT) {
                BPlusTreeFSByteArray.write(key, node.count, 0);
                scoreList.insert(key);
            }
            else
            {
                node.removeAll();
                fromSymbolToSymbol.delete(node);
            }
        }
        touched.clear();
    }

    //Returns the bit positions of the encoded fields relative to the position of dataWriter at the start.
    public static long[] encode(byte[][] input, OutputStream dictionaryWriter, OutputStream dataWriter) throws IOException {
        int totalSize = getTotalArrayLength(input) + input.length;
        Encoder result = new Encoder(totalSize >> 2);

        int[] references = new int[256];
        int[] symbols = new int[totalSize];

        int index = 0;
        for(int i = 0; i < input.length; i++) {
            for (int x = 0; x < input[i].length; x++) {
                int b = input[i][x] & 0xFF;
                if (references[b] == 0) {
                    result.symbolReferenceA[result.symbolIndex] = b;
                    result.symbolReferenceB[result.symbolIndex] = -1;
                    result.symbolSize[result.symbolIndex] = 1;
                    result.hasEndSymbol[result.symbolIndex] = false;
                    references[b] = result.symbolIndex++;
                }
                symbols[index++] = references[b];
            }
            symbols[index++] = endOfLineSymbol;
        }
        result.symbolReferenceA[endOfLineSymbol] = -1;
        result.symbolReferenceB[endOfLineSymbol] = -1;
        result.symbolSize[endOfLineSymbol] = 1;
        result.hasEndSymbol[endOfLineSymbol] = true;
        result.symbolize(symbols);

        BitStreamWriter treeWriter = new BitStreamWriter(dictionaryWriter);
        BitStreamWriter fieldWriter = dictionaryWriter == dataWriter ? treeWriter : new BitStreamWriter(dataWriter);

        long[] rowPositions = new long[input.length];
        result.writeHuffman(treeWriter, fieldWriter, rowPositions);

        treeWriter.close();
        fieldWriter.close();

        return rowPositions;
    }

    private static int getTotalArrayLength(byte[][] array)
    {
        int result = 0;
        for(int i = 0; i < array.length; i++)
            result += array[i].length;

        return result;
    }

    class CNode extends HashableLong {
        int count;
        int change;
        boolean touched;
        int firstIndex;
        private int lastIndex;

        CNode(long symbol, int index) {
            hashValue = symbol;
            count = 0;
            change = 0;
            touched = true;
            firstIndex = index;
            lastIndex = index;
        }

        void add(int index) {
            if(lastIndex == -1)
            {
                firstIndex = index;
            }
            else {
                offsetsRight[lastIndex] = index;
                offsetsLeft[index] = lastIndex;
            }

            lastIndex = index;
        }

        void removeAll()
        {
            int index = firstIndex;
            while(index > -1)
            {
                int futureIndex = offsetsRight[index];

                offsetsLeft[index] = -1;
                offsetsRight[index] = -1;

                index = futureIndex;
            }

            firstIndex = -1;
            lastIndex = -1;
        }


        void remove(int index)
        {
            int left = offsetsLeft[index];
            int right = offsetsRight[index];

            if(left > -1)
                offsetsRight[left] = right;
            if(right > -1)
                offsetsLeft[right] = left;

            offsetsLeft[index] = -1;
            offsetsRight[index] = -1;

            if(index == firstIndex)
                firstIndex = right;

            if(index == lastIndex)
                lastIndex = left;
        }

        @Override
        public String toString()
        {
            return Long.toString(hashValue);
        }
    }
}
