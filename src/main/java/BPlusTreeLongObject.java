/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

public class BPlusTreeLongObject <T> {

    private static final int DEFAULT_BRANCHING_FACTOR_INNER_NODE = 512;
    private static final int DEFAULT_BRANCHING_FACTOR = 256;

    private Node root;

    public BPlusTreeLongObject() {
        root = new LeafNode();
    }

    public T get(long key)
    {
        return root.get(key);
    }

    private void insert(long key, T value) {
        if (root.isOverflow()) {
            Node sibling = root.split();
            InternalNode newRoot = new InternalNode();
            newRoot.insertChild(0, root);
            newRoot.insertChild(1, sibling);
            root = newRoot;
        }
        root.insert(key, value);
    }

    public void delete(long key) {
        root.delete(key);
    }

//    public void addTo(long key, T value)
//    {
//        int v = root.addTo(key, value);
//        if(v == Integer.MIN_VALUE)
//            insert(key, value);
//    }

    public void set(long key, T value)
    {
        T v = root.set(key, value);
        if(v == null)
            insert(key, value);
    }

//    public void removeValuesBelow(int value)
//    {
//        Node n = root;
//        while (!(n instanceof LeafNode))
//            n = ((InternalNode) n).children[0];
//
//        LeafNode leaf = (LeafNode)n;
//        while(leaf != null)
//        {
//            int i = 0;
//            int x = 0;
//            while(x < leaf.keyCount) {
//                int v = leaf.values[x];
//                if (v < value) {
//                    x++;
//                } else {
//                    leaf.keys[i] = leaf.keys[x];
//                    leaf.values[i] = v;
//                    i++;
//                    x++;
//                }
//            }
//            leaf.keyCount = i;
//            leaf = leaf.next;
//        }
//
//        root.removeUnderflow();
//    }

    private LeafNode iterator;
    private int iteratorIndex;
    public boolean prepareIteration()
    {
        Node n = root;
        while (!(n instanceof BPlusTreeLongObject.LeafNode))
            n = ((InternalNode) n).children[0];
        iterator = (LeafNode)n;
        while(iterator != null && iterator.keyCount == 0)
            iterator = iterator.next;
        iteratorIndex = 0;
        return iterator != null;
    }

    public boolean nextIteration()
    {
        iteratorIndex++;
        if(iterator.keyCount == iteratorIndex)
        {
            iteratorIndex = 0;
            iterator = iterator.next;
            while(iterator != null && iterator.keyCount == 0)
                iterator = iterator.next;

            return iterator != null;
        }
        return true;
    }

    public long iterationKey()
    {
        return iterator.keys[iteratorIndex];
    }

    public T iterationValue()
    {
        return iterator.values[iteratorIndex];
    }

    private abstract class Node {
        long[] keys;
        int keyCount;

        abstract boolean delete(long key);

        abstract boolean insert(long key, T value);

        abstract T get(long key);

        //abstract int addTo(long key, T value);

        abstract T set(long key, T value);


        abstract void merge(Node sibling);

        abstract Node split();

        abstract boolean isOverflow();

        abstract boolean isUnderflow();

        abstract void removeUnderflow();

        abstract int size();
    }

    private class InternalNode extends Node {
        Node[] children;

        InternalNode() {
            this.keys = new long[DEFAULT_BRANCHING_FACTOR_INNER_NODE];
            keyCount = 0;
            this.children = new BPlusTreeLongObject.Node[DEFAULT_BRANCHING_FACTOR_INNER_NODE];
        }

        @Override
        T get(long key) {
            return children[getChild(key)].get(key);
        }

//        @Override
//        int addTo(long key, T value) {
//            return children[getChild(key)].addTo(key, value);
//        }

        @Override
        T set(long key, T value) {
            return children[getChild(key)].set(key, value);
        }

        @Override
        boolean delete(long key) {
            int index = getChild(key);
            Node child = children[index];
            boolean result = child.delete(key);
            keys[index] = child.keys[0];
            if (child.isUnderflow()) {
                deleteChild(index);
                if(root.keyCount == 1 && root instanceof BPlusTreeLongObject.InternalNode)
                    root = ((InternalNode)root).children[0];
            }
            return result;
        }

        @Override
        synchronized boolean insert(long key, T value) {
            int index = getChild(key);
            Node child = children[index];
            if (child.isOverflow()) {
                Node sibling = child.split();
                insertChild(index+1, sibling);
                if(keys[index+1] <= key) {
                    child = children[++index];
                }
            }
            boolean result = child.insert(key, value);
            keys[index] = child.keys[0];
            return result;
        }



        @Override
        void merge(Node sibling) {
            InternalNode node = (InternalNode) sibling;
            System.arraycopy(node.keys, 0, keys, keyCount, node.keyCount);
            System.arraycopy(node.children, 0, children, keyCount, node.keyCount);

            keyCount += node.keyCount;
        }

        @Override
        Node split() {
            InternalNode sibling = new InternalNode();

            int newLength = keyCount >> 1;
            sibling.keyCount = keyCount-newLength;

            System.arraycopy(keys, newLength, sibling.keys, 0, sibling.keyCount);
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
            return keyCount < 1;//keyCount < DEFAULT_BRANCHING_FACTOR_INNER_NODE >> 1;
        }

        @Override
        void removeUnderflow() {
            int index = 0;
            while(keyCount > 1 && index < keyCount)
            {
                if(children[index].keyCount == 0)
                {
                    deleteChild(index);
                }
                else {
                    children[index++].removeUnderflow();
                }
            }
        }

        @Override
        int size() {
            int result = 0;
            for(int i = 0; i < keyCount; i++)
                result += children[i].size();
            return result;
        }

        synchronized int getChild(long key) {
            if(keys[0] > key)
                return 0;
            if(key >= keys[keyCount-1])
                return keyCount-1;
            int loc = binarySearch(keys, keyCount, key);
            return loc >= 0 ? loc : -loc-2;
        }

        synchronized void deleteChild(int loc) {
            Node child = children[loc];

            boolean hasMerged = false;
            if(loc > 0)
            {
                Node otherChild = children[loc-1];
                if(otherChild.keyCount + child.keyCount <= otherChild.keys.length) {
                    otherChild.merge(child);
                    hasMerged = true;
                }
            }
            if(!hasMerged && loc < keyCount-1)
            {
                Node otherChild = children[loc+1];
                if(otherChild.keyCount + child.keyCount <= child.keys.length) {
                    child.merge(otherChild);
                    loc++;
                    hasMerged = true;
                }
            }
            if(hasMerged)
            {
                System.arraycopy(keys, loc+1, keys, loc, keyCount-loc-1);
                System.arraycopy(children, loc+1, children, loc, keyCount-loc-1);

                children[--keyCount]=null;
            }
        }

        synchronized void insertChild(int childIndex, Node child) {
            if (childIndex == keyCount) {
                keys[keyCount] = child.keys[0];
                children[keyCount++] = child;
            } else {
                System.arraycopy(keys, childIndex, keys, childIndex+1, keyCount-childIndex);
                System.arraycopy(children, childIndex, children, childIndex+1, keyCount-childIndex);

                keys[childIndex] = child.keys[0];
                children[childIndex] = child;
                keyCount++;
            }

        }
    }

    private class LeafNode extends Node {
        LeafNode next;
        T[] values;

        LeafNode() {
            this.keys = new long[DEFAULT_BRANCHING_FACTOR];
            this.values = (T[]) new Object[DEFAULT_BRANCHING_FACTOR];
            keyCount = 0;
        }

        private synchronized int getLocation(long key)
        {
            return (keyCount == 0 || keys[0] > key) ? -1 : (key > keys[keyCount-1]) ? -keyCount-1 : binarySearch(keys, keyCount, key);
        }

        @Override
        T get(long key) {
            int loc = getLocation(key);
            return loc >= 0 ? values[loc] : null;
        }

//        @Override
//        T addTo(long key, int value) {
//            int loc = getLocation(key);
//            if(loc < 0)
//                return null;
//            return (values[loc] += value);
//        }

        @Override
        T set(long key, T value) {
            int loc = getLocation(key);
            if(loc < 0)
                return null;
            return (values[loc] = value);
        }

        @Override
        boolean delete(long key) {
            int loc = getLocation(key);
            if (loc >= 0) {
                System.arraycopy(keys, loc+1, keys, loc, keyCount - loc-1);
                System.arraycopy(values, loc+1, values, loc, keyCount - loc-1);
                keyCount--;
                return true;
            }
            return false;
        }

        @Override
        synchronized boolean insert(long key, T value) {
            int loc = getLocation(key);
            if (loc < 0) {
                loc = -loc - 1;
                if (loc == keyCount) {
                    values[keyCount] = value;
                    keys[keyCount++] = key;
                } else {
                    System.arraycopy(keys, loc, keys, loc+1, keyCount - loc);
                    System.arraycopy(values, loc, values, loc+1, keyCount - loc);
                    keys[loc] = key;
                    values[loc] = value;
                    keyCount++;
                    return true;
                }
            }
            return false;
        }

        @Override
        void merge(Node sibling) {
            System.arraycopy(sibling.keys, 0, keys, keyCount, sibling.keyCount);
            System.arraycopy(((LeafNode)sibling).values, 0, values, keyCount, sibling.keyCount);
            keyCount += sibling.keyCount;
            next = ((LeafNode)sibling).next;
        }

        @Override
        Node split() {
            LeafNode sibling = new LeafNode();
            int newLength = keyCount >> 1;
            sibling.keyCount = keyCount-newLength;

            System.arraycopy(keys, newLength, sibling.keys, 0, sibling.keyCount);
            System.arraycopy(values, newLength, sibling.values, 0, sibling.keyCount);

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
            return keyCount < 1;//DEFAULT_BRANCHING_FACTOR >> 1;
        }

        @Override
        void removeUnderflow() {

        }

        @Override
        int size() {
            return keyCount;
        }
    }

    /*
        NEVER USE THIS FUNCTION OUTSIDE OF THIS CLASS!
        IT LACKS ANY CHECKS ON THE INPUT.
     */
    private static int binarySearch(final long[] arr, final int length, final long search) {
        int end = length - 1;
        int start = 0;

        while (start <= end) {
            int mid = start + ((end - start) >>> 1);
            long midValue = arr[mid];
            if (search < midValue) {
                end = mid - 1;
            } else if(search > midValue) {
                start = mid + 1;
            }
            else /*(arr[mid] == search)*/
                return mid;
        }
        return -start-1;
    }

}