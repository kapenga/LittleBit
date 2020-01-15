/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.util.ArrayList;
import java.util.Collections;

public class HuffmanNode implements Comparable<HuffmanNode> {
    public int symbol;
    public HuffmanNode a;
    public HuffmanNode b;
    public int frequency;
    int depth;


    public BitSet bitSet;

    public HuffmanNode(int symbol, HuffmanNode a, HuffmanNode b)
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

    public static HuffmanNode method2(HuffmanNode[] inputs)
    {
        ArrayList<HuffmanNode> result = new ArrayList<>();
        for(int i = 0; i < inputs.length; i++)
            result.add(inputs[i]);

        Collections.sort(result);

        while(result.size() > 1)
        {
            //Pick the 2 smallest nodes
            HuffmanNode b = result.remove(result.size()-1);
            HuffmanNode a = result.remove(result.size()-1);
            HuffmanNode c = new HuffmanNode(-3, a, b);
            c.frequency = a.frequency + b.frequency;
            boolean inserted = false;
            for(int i = result.size()-1; i > -1; i--)
            {
                HuffmanNode other = result.get(i);
                if(other.frequency > c.frequency)
                {
                    result.add(i+1, c);
                    inserted = true;
                    break;
                }
            }
            if(!inserted)
                result.add(0, c);
        }

        HuffmanNode root = result.get(0);
        return root;
    }



    public boolean isLeaf()
    {
        return symbol > -3;
    }

    public void getDepths(int[] depths, int currentDepth)
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
    public int compareTo(HuffmanNode o) {
        return -Integer.compare(frequency, o.frequency);
    }

    @Override
    public String toString()
    {
        return Integer.toString(symbol);
    }


}