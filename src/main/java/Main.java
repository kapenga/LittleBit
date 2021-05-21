/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {
        if(args.length < 3 || !(args[0].toLowerCase(Locale.ROOT).startsWith("-e") || args[0].toLowerCase(Locale.ROOT).startsWith("-d")))
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
                    System.out.print("Reading file: " + file.toPath());
                    byte[] data = Files.readAllBytes(file.toPath());
                    System.out.println(" done.");
                    FileOutputStream fos = new FileOutputStream(args[2]);
                    BitStreamWriter writer = new BitStreamWriter(fos);

                    System.out.print("Find symbols...");
                    Encoder2 e = Encoder2.bytesToSymbols(data);
                    System.out.println(" done.");
                    System.out.print("Write encoded data...");
                    e.write(writer);
                    System.out.println(" done.");

                    writer.close();
                    fos.close();
                    System.out.println("Total size:\t\t\t" + writer.length() + " bytes");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else //Decode
            {
                try {
                    FileInputStream fis = new FileInputStream(args[1]);
                    BitStreamReader reader = new BitStreamReader(fis);
                    FileOutputStream writer = new FileOutputStream(args[2], false);
                    DecodeNode root = CanonicalHuffmanTree.readTree(reader);
                    root.readField(reader, writer);
                    fis.close();
                    writer.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            double seconds = Duration.between(start, Instant.now()).toMillis() / 1000.0;
            System.out.println("Done in: " + seconds + " seconds");
        }
    }
}