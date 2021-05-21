# LittleBit 0.2
Author: Wybren Kapenga

### Features:
- Random Access Reading
- Multi-core decoding
- Support for multiple variable length fields / rows / files
- Fast and simple decoder
- Static model

### Current state and license:
The current version of LittleBit 0.2 should be considered an alpha version and a tech demo.
Licensed under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)  
Source code can be found on https://github.com/kapenga/LittleBit/  

### Updates
## 0.2
Introduced a new encoder that is around 300 times faster for files above 20 megabyte. This code can be found in encoder2.java.
The speed up is accomplished by keeping track of counts and positions. This increases the memory consumption from around 6x the input file to around 21x the input file.
Also the compression ratio suffers a bit because we simply take the biggest count instead of a slightly more intelligent approach.
But the result is a practically usable algorithm instead of a nice theoretical idea.
Decoding is still backwards compatible.

Generating the creation of large Huffman trees is sped up too in this version.
A small bug in the BitStreamWriter is fixed.

## 0.1
First original version. Still available in Encoder.java.

### Introduction
The idea for a new compression technique for databases was born while designing a new database engine. A key requirement is that it should be able to decode the data on a table field level while maintaining a good compression ratio. The initial question was: Is it possible to get good compression ratios while using Huffman encoding? It would require a static Huffman tree and the sum of the size of the tree and the encoded data must be competitive with other compression techniques. After creating a demo it is shown that this is indeed possible but at the cost of time to find an appropriate static Huffman tree.

### Historical similar approaches
Two algorithms are found that can be compared to LittleBit. The first one is called HuffWord, mentioned in the book ‘Managing gigabytes’ written by Ian H. Witten, Alistair Moffat, and Timothy C. Bell. The code or the program could not be found and the performance is also unknown. The second program is HuffSyllable, mentioned in ‘Syllable-based Compression’, a Ph.D. Thesis written by Jan Lansky. Additional research is done by Thomas Kuthan and Jan Lansky for the Dateso 2007 Workshop where a genetic algorithm is used to optimize HuffSyllable. In all the cases mentioned here it is unknown if the dictionary (Huffman tree) is included in the scoring of the algorithms. Also the program or algorithm is not released and the test data mentioned is not available. Therefore on this moment it is impossible to correctly compare LittleBit to these algorithms. 
A difference between HuffWord, HuffSyllable and LittleBit is that LittleBit does not have a language specific preprocessor or any other trick to improve compression ratios by using predefined knowledge about the data it has to encode.

LittleBit was developed without prior knowledge of other programs with similar approaches.

## Technical specifications:
### Encoding
The program encodes in 2 stages. During the first stage the program tries to find an optimal static Huffman tree for the input. Unlike most classical static Huffman trees the leafs can represent one or more bytes. The tree is saved as a canonical Huffman tree. Instead of writing the bytes, the multi-byte leafs have a reference to the 2 ‘parent’ leafs. This can be multiple layers deep.
Finding the optimal static Huffman tree is a NP-hard task. A (sort of) lexicographic breadth first approach (without backtracking) is developed to find a fair but sub-optimal solution in a time span that is not near infinite. Or in words I would use: “It’s a one way trip using educated guesses that seems to work okay’ish somehow.”
A ‘perfect’ version of finding the optimal outcome in this NP-hard task can be made using a recursive full search, but the calculation time for even small files would be almost infinite.
Memory usage during the first stage is between 20 or 21 times the size of the input.
In the second and last stage the data is encoded using the canonical Huffman tree that is found in stage1.
The Huffman tree can be stored separately of the encoded data.
### Decoding
After loading the canonical Huffman tree, the data can be retrieved as when decoding a classical static Huffman tree. Very fast decoding should be possible because of the small, simple and static nature of the Huffman tree.

### Multi-core and random access reading
The static Huffman tree can be shared among multiple cores (or even machines) to support multi-core decoding.
The data can be decoded on leaf-level if the bit position of the start of the leaf is known. A use case can be a database index referencing the bit positions of the compressed fields. Using the static Huffman tree, the database engine (or client!) can decode the information that is stored in a particular field.
Another use case for random access or multi-core reading is when the data is stored in fixed size blocks of for example 1 kilobyte. This can be useful for games and other software that uses a large amount of read only data that needs to be randomly accessed.

### Results using encoder version 2
|File|Size|Huffman tree|Data|Total|Encoding time|
|----|----:|------------:|----:|-----:|-------------:|
|acrord32.exe|3.870.784|211.279|1.821.209|2.032.488|4 s|
|Book1|768.771|29.517|242.725|272.242|1 s|
|Kingjames.txt|4.452.519|115.612|1.010.188|1.125.800|3 s|
|Fp.log|20.617.071|152.889|660.066|812.955|8 s|
|Enwik8|100.000.000|2.226.313|24.529.615|26.755.928|86 s|

These tests are done on a 2017 model of a Macbook Pro and the time measurements should be considered only as an indication. Encoding times are for 99.9% the stage 1 model searching.
Decoding times are not mentioned because the source code is written in object oriented Java and decoding should be magnitudes faster in c(++) with a proper lookup table.

|File|Size|LittleBit 0.2|BZip2|LZMA2|Deflate|
|----|---:|------------:|----:|----:|------:|
|acrord32.exe|3.870.784|2.032.488|1.699.876|1.288.887|1.680.482|
|Book1|768.771|272.242|232.438|261.111|300.819|
|Kingjames.txt|4.452.519|1.125.800|1.001.852|1.056.883|1.325.449|
|Fp.log|20.617.071|812.955|724.085|927.511|1.406.955|
|Enwik8|100.000.000|26.755.928|29.011.932|26.080.305|35.194.836|

LittleBit 0.2 compared to other algorithms. The encoding is done using 7zip application and BZip2, LZMA2 and Deflate are configured with the ‘normal’ setting.

As shown the algorithm works well on natural languages or XML like structures. The results are comparable to commonly used compression algorithms. The algorithm starts to lag behind when there is no solution found to create a small library that effectively represents the structure of the data. This is especially shown in case of executable files.
However this algorithm is originally designed for usage in databases. When used on columns containing natural text or repeating (enumeration) values, it should be able to outperform the currently available techniques at the cost of the time needed to find a model that effectively maps the structure of the data. [Todo: insert database compression comparisons]

(The testfiles ‘acrord32.exe’ and ‘fp.log’ are downloaded from the website https://www.maximumcompression.com. ‘Book1’ is part of the well known Calgary Corpus. ‘Kingjames.txt’ can be found on http://www.gutenberg.org/ebooks/10 and ‘enwik8’ is a file from the Hutter prize website.)


### The model searcher

> It was a |fine |morning|, and the |sun |lighted up |to |a  
|scarlet |glow| the |cri|m|son |j|ac|ke|t |she |wor|e|, and |pain|ted  
|a |soft |lustre |upon her |bright |face |and |dark |hair|. The  
|myrtle|s, |g|er|an|i|um|s, and |c|actu|ses |packed |around |her  
|were |fre|sh and |green|, and |at |such a |leaf|less |season| they  
|in|ve|st|ed the |whole |concern |of |horse|s, |waggon, |furnit|ure|,  
and |girl |with a |peculiar |ver|n|al |charm|. What |possesse|d  
|her |to |indul|ge |in such a |performance |in the |sight |of the  
|spar|row|s, |black|bird|s, and |un|perceived |farmer |who |were  
|alone |its |spectators, |-- |whe|ther| the |smile |began |as a  
|fact|iti|ous |one, |to |te|st |her |capacity |in that |ar|t, |-- |nobody  
|knows |; |it |ended |certainly |in a |real |smile|. She |blushe|d  
|at |herself|, and |seeing |her |reflection |blush, |blushed |the  
|more|.  

The text above a small citation from Book1. The text between the pipe symbols represent one Huffman tree leaf. As can be seen here, the algorithm is able to map the text to a basic language model while keeping the sum of the size of the Huffman tree and the size of the data as small as possible.  
This leads to a few choices that are strange on first sight. For example the above text contains ‘blushed’ 2 times. The first time this word is divided in ‘blushe’ and ‘d\n’. The second time it is undivided and has an extra space character on the end. Apparently the algorithm has decided that ‘blushe’ and ‘d_’ has enough occurrences to warrant an extra Huffman tree leaf, resulting in a leaf with ‘blushed_’ while ‘blushe’ and ‘d\n’ (without the space character but with a newline character) is more effectively stored as 2 separate Huffman tree leaves.

The following steps lead to the results shown above:
1. Convert bytes to symbols. (Convert bits to symbols for bit streams)
2. Count every symbol.
3. Count every combination of two symbols that occur next to each other.
4. Remove all symbol combinations with a count lower than 4 (for performance reasons).
5. Find the symbol combination that should be the best symbol combination to merge.
	(This is a gamble and at best an educated guess.)
	The formula that leads to good results is; for every symbol combination:
	score = frequency * (0.2 + max * 3 + min) – minCount
	Where frequency is the count of the symbol combination. 0.2 is an ad hoc constant that 	seems to improve results. The ‘max’ value is the highest ratio of a single symbol frequency 	compared to the symbol combination frequency (combination frequency / symbol 	frequency). The ‘min’ value is the lowest ratio. The minCount value is a constant value of 4.
6. If there is no score above 0: go to step 10.
7. Merge the symbol combination with the highest score to a new symbol.
8. Update the symbol and symbol combination statistics.
9. Go to step 4.
10. Create a canonical Huffman tree using the symbols and their frequency.
11. Encode the data using the Huffman tree.

The somewhat random looking frequency ratio (min/max) in the scoring mechanism has an explainable existence. A symbol merge has a negative impact on the frequency of the 2 symbols involved but also on the frequency of the symbol combination that are neighbours of the symbol combination that will be merged. This negatively impacts the position in the Huffman tree for those symbols. A high maximum ratio is a good indicator that the two, soon to be merged, symbols have a strong statistical relation to each other and overall compression ratios will improve by merging. Giving the best ratio a higher weight but also involving the lowest ratio seems to be the best option. Better indicators of a possible best merge are subject of future research.  
For larger files (like enwik8 or bigger) an adjusted scoring mechanism is probably better. Because of the bigger size of the library the minimum frequency should probably be 5 or even higher.  

### Additional research
Several additional techniques are tried out to improve compression. The most successful technique was combining symbols while allowing a fixed size gap. Binary data is often dividable in fixed size parts of for example 4 bytes in case of a 32 bits integer array. This technique made it possible to find those repeating occurrences for additional compression power and improved the compression of binary data by around 1% and text by around 0.5%, but at a great cost of at least tripling encoding time. Also decoding would be more complex with recursive patterns. Furthermore it hurt the possibility of the random access reading of a piece of data. Therefor this technique is not presented here.

Another technique worth mentioning is calculating the size of the Huffman tree and data for every possible symbol merge as a way of scoring and finding the best possible option. Surprisingly this hurt compression ratios while being a costly calculation. Making decisions bases on this scoring mechanism turned out to be worse compared to the simple scoring mechanism. A secondary problem was that this scoring mechanism is vulnerable to local optima.

The technique used in LittleBit can be described as a bottom-up approach. Symbols representing just 1 byte or bit are combined to form larger patterns. A top-down approach, starting with larger sets of commonly occurring symbols and work back to the atomic set of symbols, is also possible, but in reality it is hard to construct a well balanced static Huffman tree this way. Compression ratios turn out to be much worse. Large patterns are well compressed, but much more commonly occurring small patterns take more bits to encode.

The fourth and last technique to mention is using statistics to find natural ‘dividers’ in the data. The idea behind this is that language optimized models such as HuffWord use knowledge to separate words from nonwords to improve compression ratios. Using these ‘dividers’ in the scoring mechanism did not improve compression. It might be possible that LittleBit is able to generate better compression by not dividing the words and the nonwords and that acknowledging common combinations of those two types turn out to be more effective in the end.

### Possible improvements
When sacrificing random access reading, simplicity or the static nature of this algorithm, improvements of compression ratios are easy to make. Replacing Huffman coding with more entropy optimal encoders, using context aware predictors or even Paq-like model mixing could make a huge difference, but is beyond the scope of this project.  
Better static Huffman trees can be found when using more time costly algorithms such as genetic algorithms and deep recursive searches. Both topics are high on the wish list to be researched but will hurt practical use because of the increase in the cost of encoding time.

### Conclusion
For me personally it came as a surprise that it was possible to create a program that encodes structured information and maintaining average scoring compression ratios, while using a static Huffman tree.  
For now the time cost of encoding is impractical and needs further research. Also additional, but probably small, improvements can be made on the stage 1 algorithm to find a more optimal Huffman tree.  
It would not surprise me if some of the research done here end up in other algorithms. For example the LZ77 family of encoders would benefit from the structures found by the stage 1 algorithm. This way it must be possible to find a lower amount of length-distance pairs (that are also more alike) to represent a piece of data.
