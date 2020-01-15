/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class Main {
    public static void main(String[] args) throws Exception {
        if(args.length < 3)
        {
            System.out.println("Usage: -e | -d [source file] [destination file]");
            System.out.println("-e for encode. -d for decode.");
        }
        else {
            boolean encode = args[0].toLowerCase(Locale.ROOT).startsWith("-e");
            String filename = args[1];
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("The source file is not found.");
                return;
            }
            Instant start = Instant.now();
            if (encode) {
                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    BitStreamWriter writer = new BitStreamWriter(args[2]);

                    Encoder.Process(new byte[][]{data}, writer);
                    writer.close();
                    System.out.println(writer.length());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                BitStreamReader reader = new BitStreamReader(args[1]);
                FileOutputStream writer = new FileOutputStream(args[2], false);
                DecodeNode root = CanonicalHuffmanTree.readTree(reader);
                root.readField(reader, writer);
                reader.close();
                writer.close();
            }
            double seconds = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            System.out.println("Done in: " + seconds + " seconds");
        }
    }
}