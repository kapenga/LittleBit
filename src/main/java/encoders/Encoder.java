package encoders;

import decoders.Decoder;
import io.BitStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class Encoder {
    private final static int endOfLineSymbol = 0;
    private int symbolIndex = 1;
    private int[] symbolReferenceA = new int[MAXSYMBOLCOUNT];
    private int[] symbolReferenceB = new int[MAXSYMBOLCOUNT];
    private int[] symbolCount = new int[MAXSYMBOLCOUNT];
    private int[] symbolSize = new int[MAXSYMBOLCOUNT];
    private boolean[] hasEndSymbol = new boolean[MAXSYMBOLCOUNT];

    private int[] symbols;

    private final static int MINIMALCOUNT = 4;
    private final static int MAXSYMBOLCOUNT = 1 << 24;

    private void symbolize(int[] symbols) {
        /*
            1. Find most common symbol pair

            2. Make it a new symbol
            3. Repeat
         */
        HashTable fromSymbolToSymbol = new HashTable(Decoder.bitSize(symbols.length)-4);
        BPlusTreeFSByteArray scoreList = new BPlusTreeFSByteArray(12);
        ArrayList<CNode> touched = new ArrayList<>();
        this.symbols = symbols;

        int[] offsetsLeft = new int[symbols.length];
        int[] offsetsRight = new int[symbols.length];

        for (int i = 0; i < offsetsLeft.length; i++) {
            offsetsLeft[i] = -1;
            offsetsRight[i] = -1;
        }

        long lastSymbol = -1;
        for (int i = 0; i < symbols.length - 1; i++) {
            symbolCount[symbols[i]]++;
            long currentSymbol = ((long) symbols[i] << 32) + symbols[i + 1];

            addSymbol(symbols[i], symbols[i + 1], i, currentSymbol != lastSymbol, true, true, offsetsLeft, offsetsRight, fromSymbolToSymbol, touched);

            if (currentSymbol == lastSymbol)
                lastSymbol = -1;
            else
                lastSymbol = currentSymbol;
        }
        symbolCount[symbols[symbols.length - 1]]++;


        while (true) {
            processTouched(offsetsLeft, offsetsRight, fromSymbolToSymbol, touched, scoreList);

            if (scoreList.size() < 1) {
                System.out.println(" done.");
                System.out.println("Number of symbols:\t" + symbolIndex);
                break;
            }

            byte[] lastElement = scoreList.removeLast();
            long key = BPlusTreeFSByteArray.readLong(lastElement, 4);

            CNode winner = (CNode)fromSymbolToSymbol.get(key);

            symbolReferenceA[symbolIndex] = winner.symbolA;
            symbolReferenceB[symbolIndex] = winner.symbolB;
            symbolSize[symbolIndex] = symbolSize[winner.symbolA] + symbolSize[winner.symbolB];
            hasEndSymbol[symbolIndex] = hasEndSymbol[winner.symbolB];
            int newSymbol = symbolIndex++;
            fromSymbolToSymbol.delete(winner);

            int nextOffset = symbolSize[winner.symbolA];

            int index = winner.firstIndex;
            long lastTriggerSymbol = -1;
            while (index > -1) {
                int futureIndex = offsetsRight[index];
                if (symbols[index] > -1) {

                    int previousIndex = index;
                    while(--previousIndex > -1 && symbols[previousIndex] < 0);

                    if (previousIndex > -1) {
                        lastSymbol = (((long) symbols[previousIndex]) << 32) + symbols[index];
                        removeSymbol(lastSymbol, previousIndex, offsetsLeft, offsetsRight, fromSymbolToSymbol, touched);
                    }

                    int nextIndex = index + nextOffset;

                    int nextNextIndex = nextIndex;
                    while(++nextNextIndex < symbols.length && symbols[nextNextIndex] < 0);

                    if (nextNextIndex < symbols.length) {
                        lastSymbol = (((long) winner.symbolB) << 32) + symbols[nextNextIndex];

                        if (lastSymbol != key) {
                            removeSymbol(lastSymbol, nextIndex, offsetsLeft, offsetsRight, fromSymbolToSymbol, touched);
                        }
                    }

                    winner.remove(offsetsLeft, offsetsRight, index);
                    symbolCount[winner.symbolA]--;
                    symbolCount[winner.symbolB]--;
                    symbolCount[newSymbol]++;
                    symbols[index] = newSymbol;
                    symbols[nextIndex] = -1;

                    if (previousIndex > -1) {
                        long previousSymbol = (((long) symbols[previousIndex]) << 32) + newSymbol;
                        addSymbol(symbols[previousIndex], newSymbol, previousIndex, previousSymbol != lastTriggerSymbol, false, true, offsetsLeft, offsetsRight, fromSymbolToSymbol, touched);

                        if (previousSymbol != lastTriggerSymbol && symbols[previousIndex] == newSymbol)
                            lastTriggerSymbol = previousSymbol;
                        else
                            lastTriggerSymbol = -1;
                    }

                    if (nextNextIndex < symbols.length) {
                        addSymbol(newSymbol, symbols[nextNextIndex], index, true, true, false, offsetsLeft, offsetsRight, fromSymbolToSymbol, touched);
                    }
                }

                index = futureIndex;
            }

        }
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

    private void addSymbol(int symbolA, int symbolB, int index, boolean countMe, boolean forceFirst, boolean forceSecond, int[] offsetsLeft, int[] offsetsRight, HashTable fromSymbolToSymbol, ArrayList<CNode> touched)
    {
        if(hasEndSymbol[symbolA] || (!forceFirst && symbolCount[symbolA] < MINIMALCOUNT) || (!forceSecond && symbolCount[symbolB] < MINIMALCOUNT))
            return;
        long symbol = (((long)symbolA) << 32) + symbolB;
        CNode node = (CNode)fromSymbolToSymbol.get(symbol);
        if(node == null)
        {
            node = new CNode(symbolA, symbolB, index);
            fromSymbolToSymbol.add(node);
            touched.add(node);
        }
        else
        {
            if(!node.touched) {
                touched.add(node);
                node.touched = true;
            }
            node.add(offsetsLeft, offsetsRight, index);
        }
        if(countMe) {
            node.change++;
        }
    }

    private void removeSymbol(long symbol, int index, int[] offsetsLeft, int[] offsetsRight, HashTable fromSymbolToSymbol, ArrayList<CNode> touched)
    {
        CNode node = (CNode) fromSymbolToSymbol.get(symbol);
        if(node != null) {
            if(!node.touched) {
                touched.add(node);
                node.touched = true;
            }
            node.change--;
            node.remove(offsetsLeft, offsetsRight, index);
        }
    }

    private void processTouched(int[] offsetsLeft, int[] offsetsRight, HashTable fromSymbolToSymbol, ArrayList<CNode> touched, BPlusTreeFSByteArray scoreList)
    {
        byte[] key = new byte[12];
        for(CNode node : touched)
        {
            BPlusTreeFSByteArray.write(key, node.count, 0);
            BPlusTreeFSByteArray.write(key, node.symbol, 4);

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
                node.removeAll(offsetsLeft, offsetsRight);
                fromSymbolToSymbol.delete(node);
            }
        }
        touched.clear();
    }

    //Returns the bit positions of the encoded fields relative to the position of dataWriter at the start.
    public static long[] encode(byte[][] input, OutputStream dictionaryWriter, OutputStream dataWriter) throws IOException {
        Encoder result = new Encoder();

        int[] references = new int[256];
        int[] symbols = new int[getTotalArrayLength(input) + input.length];

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

    class CNode implements HashableLong {
        final int symbolA;
        final int symbolB;
        final long symbol;

        int count;
        int change;
        boolean touched;
        int firstIndex;
        private int lastIndex;

        CNode(int symbolA, int symbolB, int index) {
            this.symbolA = symbolA;
            this.symbolB = symbolB;
            this.symbol = (((long)symbolA) << 32) + symbolB;
            count = 0;
            change = 0;
            touched = true;
            firstIndex = index;
            lastIndex = index;
        }

        void add(int[] offsetLeft, int[] offsetRight, int index) {
            if(lastIndex == -1)
            {
                firstIndex = index;
            }
            else {
                offsetRight[lastIndex] = index;
                offsetLeft[index] = lastIndex;
            }

            lastIndex = index;
        }

        void removeAll(int[] offsetLeft, int[] offsetRight)
        {
            int index = firstIndex;
            while(index > -1)
            {
                int futureIndex = offsetRight[index];

                offsetLeft[index] = -1;
                offsetRight[index] = -1;

                index = futureIndex;
            }

            firstIndex = -1;
            lastIndex = -1;
        }


        void remove(int[] offsetLeft, int[] offsetRight, int index)
        {
            int left = offsetLeft[index];
            int right = offsetRight[index];


            if(left > -1) {
                offsetRight[left] = right;
            }
            if(right > -1)
                offsetLeft[right] = left;

            offsetLeft[index] = -1;
            offsetRight[index] = -1;

            if(index == firstIndex)
                firstIndex = right;

            if(index == lastIndex)
                lastIndex = left;
        }

        @Override
        public long getHash() {
            return symbol;
        }

        @Override
        public String toString()
        {
            return Long.toString(symbol);
        }
    }

}
