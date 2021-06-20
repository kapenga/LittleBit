/*
Written by Wybren Kapenga

Licenced under CC BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)

TODO:
- Improve decoding. Its okay but not high performance.
- There will be a problem decoding when the endOfLine symbols are included into other symbols.
- Recreate the option to compress more than one field.
- Create an encoder that can work with very big files that do not fit into RAM.
- Create an encoder that can do recursive looking for the optimal encoding.
 */

import decoders.Decoder;
import encoders.Encoder;
import io.BitStreamReader;
import io.BitStreamWriter;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class Main {

    private static void printHelp() {
        System.out.println("Usage for a single file: -e | -d [source file] [destination file]");
        System.out.println("-e for encode. -d for decode.");
        System.out.println("Usage for encoding a directory: -f [source directory]");
    }

    private static void encode(String filenameIn, String filenameOut) {
        File file = new File(filenameIn);
        if (!file.exists()) {
            System.out.println("The source file is not found.");
            return;
        }
        Instant start = Instant.now();
        try {
            System.out.print("Reading file: " + file.toPath());
            byte[] data = Files.readAllBytes(file.toPath());
            System.out.println(" done.");
            FileOutputStream fos = new FileOutputStream(filenameOut);

            System.out.print("Find symbols...");
            Encoder.encode(new byte[][]{data}, fos, fos);

            fos.close();
            System.out.println("Total size:\t\t\t" + (new File(filenameOut).length()) + " bytes");
        } catch (Exception e) {
            e.printStackTrace();
        }

        double seconds = Duration.between(start, Instant.now()).toMillis() / 1000.0;
        System.out.println("Encoding done in:\t" + seconds + " seconds");

    }

    private static void encodeFolder(String directoryIn) {
        File file = new File(directoryIn);
        if (!file.isDirectory()) {
            System.out.println("The source directory is not found.");
            return;
        }

        for(File f : file.listFiles()) {
            if(!f.getName().endsWith(".bits")) {
                encode(f.getPath(), f.getPath() + ".bits");
            }
        }

    }


    private static void decode(String filenameIn, String filenameOut) {
        File file = new File(filenameIn);
        if (!file.exists()) {
            System.out.println("The source file is not found.");
            return;
        }
        Instant start = Instant.now();
        try {
            FileInputStream fis = new FileInputStream(filenameIn);
            BitStreamReader reader = new BitStreamReader(fis);
            FileOutputStream writer = new FileOutputStream(filenameOut, false);
            BufferedOutputStream bufferedWriter = new BufferedOutputStream(writer, 1 << 20);
            Decoder decoder = new Decoder(reader);
            decoder.readField(reader, bufferedWriter);
            bufferedWriter.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double seconds = Duration.between(start, Instant.now()).toMillis() / 1000.0;
        System.out.println("Decoding done in: " + seconds + " seconds");
    }



    public static void main(String[] args) {
        String operation = args.length < 1 ? "" : args[0];

        switch (operation)
        {
            case "-e": //Encode
            {
                if(args.length < 3)
                {
                    printHelp();
                    break;
                }
                encode(args[1], args[2]);
                break;
            }
            case "-d": //Decode
            {
                if(args.length < 3)
                {
                    printHelp();
                    break;
                }
                decode(args[1], args[2]);
                break;
            }
            case "-f": //Encode directory
            {
                if(args.length < 2)
                {
                    printHelp();
                    break;
                }
                encodeFolder(args[1]);
                break;
            }

            default:
            {
                printHelp();
            }
        }
    }
}