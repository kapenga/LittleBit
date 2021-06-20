package encoders;/*
    Memory based B+ Tree for storing fixed size byte arrays.
    Build as an alternative for RoaringBitmaps. RoaringBitmaps is fast and good, but not for large lists of >32 bit values.
    This class has a better performance and memory usage as Roaring64NavigableMap in case of >64 bit values and more than 1.000.000 items.
    RoaringBitmaps is a better alternative for 32bit integers.

    B-plus-tree from jiaguofang (https://github.com/jiaguofang/b-plus-tree) was the base for this code. Its now heavily modified.
 */

class BPlusTreeFSByteArray {

    private static final int DEFAULT_BRANCHING_FACTOR_INNER_NODE = 1024;
    private static final int DEFAULT_BRANCHING_FACTOR = 256;

    private final int fixedSize;
    private Node root;
    private int count;

    BPlusTreeFSByteArray(int fixedSize) {
        this.fixedSize = fixedSize;
        root = new LeafNode();
        count = 0;
    }

    private static int compareKey(byte[] a, int offset, byte[] b)
    {
        for (int i = 0; i < b.length; i++) {
            int aByte = a[offset + i] & 0xFF;
            int bByte = b[i] & 0xFF;
            if (aByte < bByte)
                return -1;
            else if (aByte > bByte)
                return 1;
        }
        return 0;
    }

    private int binarySearch(final byte[] arr, final int length, final byte[] search) {
        int end = length - 1;
        int start = 0;

        if(length == 0)
            return -1;

        int compareStart = compareKey(arr, start, search);
        if(compareStart == 0)
            return 0;
        if(compareStart > 0)
            return -1;

        int compareEnd = compareKey(arr, end * search.length, search);
        if(compareEnd == 0)
            return length-1;
        if(compareEnd < 0)
            return -length-1;

        while (start <= end) {
            int mid = start + ((end - start) >>> 1);
            int compare = compareKey(arr, mid * search.length, search);
            if (compare > 0) {
                end = mid - 1;
            } else if(compare < 0) {
                start = mid + 1;
            }
            else /*(arr[mid] == search)*/
                return mid;
        }
        return -start-1;
    }

    void insert(byte[] key) {
        if (root.isOverflow()) {
            Node sibling = root.split();
            InternalNode newRoot = new InternalNode();
            newRoot.insertChild(0, root);
            newRoot.insertChild(1, sibling);
            root = newRoot;
        }
        if(root.insert(key))
            count++;
    }

    void delete(byte[] key) {
        if(root.delete(key))
            count--;
    }

    byte[] removeFirst()
    {
        count--;
        return root.removeFirst();
    }

    byte[] removeLast()
    {
        count--;
        return root.removeLast();
    }

    long size()
    {
        return count;
    }

    static int readInt(byte[] data, int index)
    {
        return (((data[index] & 0xFF) << 24) | ((data[index+1] & 0xFF) << 16) |((data[index+2] & 0xFF) << 8) | (data[index+3] & 0xFF));
    }

    static int write(byte[] data, int value, int index)
    {
        data[index] = (byte)(value >> 24);
        data[index+1] = (byte)(value >> 16);
        data[index+2] = (byte)(value >> 8);
        data[index+3] = (byte) value;
        return index + 4;
    }

    static int write(byte[] data, long value, int index)
    {
        data[index] = (byte)(value >> 56);
        data[index+1] = (byte)(value >> 48);
        data[index+2] = (byte)(value >> 40);
        data[index+3] = (byte)(value >> 32);
        data[index+4] = (byte)(value >> 24);
        data[index+5] = (byte)(value >> 16);
        data[index+6] = (byte)(value >> 8);
        data[index+7] = (byte) value;
        return index + 8;
    }

    static long readLong(byte[] data, int index)
    {
        return ((((long)data[index] & 0xFF) << 56) |(((long)data[index+1] & 0xFF) << 48) |(((long)data[index+2] & 0xFF) << 40) |(((long)data[index+3] & 0xFF) << 32) |(((long)data[index+4] & 0xFF) << 24) | (((long)data[index+5] & 0xFF) << 16) | (((long)data[index+6] & 0xFF) << 8) | ((long)data[index+7] & 0xFF));
    }

    private abstract class Node {
        byte[] keys;
        int keyCount;

        abstract boolean delete(byte[] key);

        abstract boolean insert(byte[] key);

        abstract boolean has(byte[] key);

        abstract void merge(Node sibling);

        abstract Node split();

        abstract boolean isOverflow();

        abstract boolean isUnderflow();

        abstract byte[] removeFirst();
        abstract byte[] removeLast();

    }

    private class InternalNode extends Node {
        Node[] children;

        InternalNode() {
            this.keys = new byte[DEFAULT_BRANCHING_FACTOR_INNER_NODE * fixedSize];
            keyCount = 0;
            this.children = new Node[DEFAULT_BRANCHING_FACTOR_INNER_NODE];
        }

        @Override
        synchronized boolean has(byte[] key) {
            return children[getChild(key)].has(key);
        }

        @Override
        synchronized boolean delete(byte[] key) {
            int index = getChild(key);
            Node child = children[index];
            boolean result = child.delete(key);
            System.arraycopy(child.keys, 0, keys, index * fixedSize, fixedSize);
            if (child.isUnderflow()) {
                deleteChild(index);
                if(root.keyCount == 1 && root instanceof InternalNode)
                    root = ((InternalNode)root).children[0];
            }
            return result;
        }

        @Override
        synchronized boolean insert(byte[] key) {
            int index = getChild(key);
            Node child = children[index];
            if (child.isOverflow()) {
                Node sibling = child.split();
                insertChild(index+1, sibling);
                if(compareKey(keys, (index+1) * fixedSize, key) <= 0) {
                    child = children[++index];
                }
            }
            boolean result = child.insert(key);
            System.arraycopy(child.keys, 0, keys, index * fixedSize, fixedSize);
            return result;
        }


        @Override
        void merge(Node sibling) {
            InternalNode node = (InternalNode) sibling;
            System.arraycopy(node.keys, 0, keys, keyCount * fixedSize, node.keyCount * fixedSize);
            System.arraycopy(node.children, 0, children, keyCount, node.keyCount);

            keyCount += node.keyCount;
        }

        @Override
        Node split() {
            InternalNode sibling = new InternalNode();

            int newLength = keyCount >> 1;
            sibling.keyCount = keyCount-newLength;

            System.arraycopy(keys, newLength * fixedSize, sibling.keys, 0, sibling.keyCount * fixedSize);
            System.arraycopy(children, newLength, sibling.children, 0, sibling.keyCount);

            keyCount = newLength;

            return sibling;
        }

        @Override
        boolean isOverflow() {
            return keyCount == DEFAULT_BRANCHING_FACTOR_INNER_NODE;
        }

        @Override
        boolean isUnderflow() {
            return keyCount < DEFAULT_BRANCHING_FACTOR_INNER_NODE >> 1;
        }

        @Override
        byte[] removeFirst() {
            Node child = children[0];
            byte[] result = child.removeFirst();

            System.arraycopy(child.keys, 0, keys, 0, fixedSize);
            if (child.isUnderflow()) {
                deleteChild(0);
                if(root.keyCount == 1 && root instanceof InternalNode)
                    root = ((InternalNode)root).children[0];
            }
            return result;
        }

        @Override
        byte[] removeLast() {
            Node child = children[keyCount-1];
            byte[] result = child.removeLast();

            if (child.keyCount == 0) {
                children[--keyCount] = null;
                if(root.keyCount == 1 && root instanceof InternalNode)
                    root = ((InternalNode)root).children[0];
            }
            return result;
        }

        int getChild(byte[] key) {
            int loc = binarySearch(keys, keyCount, key);
            int result = loc >= 0 ? loc : -loc-2;
            if(result < 0)
                result = 0;
            return result;
        }

        void deleteChild(int loc) {
            Node child = children[loc];

            boolean hasMerged = false;
            if(loc > 0)
            {
                Node otherChild = children[loc-1];
                if((otherChild.keyCount + child.keyCount) * fixedSize <= otherChild.keys.length) {
                    otherChild.merge(child);
                    hasMerged = true;
                }
            }
            if(!hasMerged && loc < keyCount-1)
            {
                Node otherChild = children[loc+1];
                if((otherChild.keyCount + child.keyCount) * fixedSize <= child.keys.length) {
                    child.merge(otherChild);
                    loc++;
                    hasMerged = true;
                }
            }
            if(hasMerged) {
                System.arraycopy(keys, (loc+1) * fixedSize, keys, loc * fixedSize, (keyCount-loc-1) * fixedSize);
                System.arraycopy(children, loc+1, children, loc, keyCount-loc-1);

                children[--keyCount]=null;
            }
        }

        void insertChild(int childIndex, Node child) {
            if (childIndex < keyCount) {
                System.arraycopy(keys, childIndex * fixedSize, keys, (childIndex + 1) * fixedSize, (keyCount - childIndex) * fixedSize);
                System.arraycopy(children, childIndex, children, childIndex + 1, keyCount - childIndex);
            }
            System.arraycopy(child.keys, 0, keys, childIndex * fixedSize, fixedSize);
            children[childIndex] = child;
            keyCount++;
        }
    }

    private class LeafNode extends Node {
        LeafNode next;

        LeafNode() {
            this.keys = new byte[DEFAULT_BRANCHING_FACTOR * fixedSize];
            keyCount = 0;
        }

        private int getLocation(byte[] key) {
            return binarySearch(keys, keyCount, key);
        }

        @Override
        synchronized boolean has(byte[] key) {
            return getLocation(key) >= 0;
        }


        @Override
        synchronized boolean delete(byte[] key) {
            int loc = getLocation(key);
            if (loc >= 0) {
                System.arraycopy(keys, (loc + 1) * fixedSize, keys, loc * fixedSize, (keyCount - loc - 1) * fixedSize);
                keyCount--;
                return true;
            }

            return false;
        }

        @Override
        synchronized boolean insert(byte[] key) {
            int loc = getLocation(key);
            if (loc < 0) {
                loc = -loc - 1;
                if (loc < keyCount)
                    System.arraycopy(keys, loc * fixedSize, keys, (loc + 1) * fixedSize, (keyCount - loc) * fixedSize);
                System.arraycopy(key, 0, keys, loc * fixedSize, fixedSize);
                keyCount++;
                return true;
            }
            return false;
        }

        @Override
        void merge(Node sibling) {
            System.arraycopy(sibling.keys, 0, keys, keyCount * fixedSize, sibling.keyCount * fixedSize);
            keyCount += sibling.keyCount;
            next = ((LeafNode) sibling).next;
        }

        @Override
        Node split() {
            LeafNode sibling = new LeafNode();
            int newLength = keyCount >> 1;
            sibling.keyCount = keyCount - newLength;

            System.arraycopy(keys, newLength * fixedSize, sibling.keys, 0, sibling.keyCount * fixedSize);

            keyCount = newLength;

            sibling.next = next;
            next = sibling;
            return sibling;
        }

        @Override
        boolean isOverflow() {
            return keyCount == DEFAULT_BRANCHING_FACTOR;
        }

        @Override
        boolean isUnderflow() {
            return keyCount < DEFAULT_BRANCHING_FACTOR >> 1;
        }

        @Override
        byte[] removeFirst() {
            byte[] result = new byte[fixedSize];
            System.arraycopy(keys, 0, result, 0, fixedSize);

            System.arraycopy(keys, fixedSize, keys, 0, (keyCount - 1) * fixedSize);
            keyCount--;

            return result;
        }

        @Override
        byte[] removeLast() {
            byte[] result = new byte[fixedSize];
            System.arraycopy(keys, (keyCount - 1) * fixedSize, result, 0, fixedSize);
            keyCount--;
            return result;
        }
    }
}
